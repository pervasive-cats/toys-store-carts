/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ForkJoinPool
import java.util.regex.Pattern
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.matching.Regex
import akka.actor.ActorRef as UntypedActorRef
import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.ws.*
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.CompletionStrategy
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import eu.timepit.refined.auto.autoUnwrap
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, Ignore, Tag}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import spray.json.JsBoolean
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.enrichAny
import spray.json.enrichString
import application.actors.DittoActor.DittoError
import application.actors.DittoCommand.*
import application.actors.MessageBrokerCommand.ItemAddedToCart
import application.actors.RootCommand.Startup
import application.routes.entities.Response.*
import application.Serializers.given
import carts.cart.valueobjects.*
import carts.cart.Repository.CartNotFound
import carts.cart.valueobjects.item.{CatalogItem, ItemId}
import carts.cart.Repository
import carts.cart.domainevents.{ItemInsertedIntoCart, ItemAddedToCart as ItemAddedToCartEvent}
import carts.cart.entities.* // scalafix:ok

@DoNotDiscover
class DittoActorTest extends AnyFunSpec with BeforeAndAfterAll with SprayJsonSupport {

  private val testKit: ActorTestKit = ActorTestKit()
  private val rootActorProbe: TestProbe[RootCommand] = testKit.createTestProbe[RootCommand]()
  private val messageBrokerActorProbe: TestProbe[MessageBrokerCommand] = testKit.createTestProbe[MessageBrokerCommand]()
  private val responseProbe: TestProbe[Validated[Unit]] = testKit.createTestProbe[Validated[Unit]]()
  private val serviceProbe: TestProbe[DittoCommand] = testKit.createTestProbe[DittoCommand]()
  private val config: Config = ConfigFactory.load()
  private val repositoryConfig: Config = config.getConfig("repository")
  private val dittoConfig: Config = config.getConfig("ditto")

  private given ActorSystem = testKit.system.classicSystem
  private val client: HttpExt = Http()
  private val repository: Repository = Repository(repositoryConfig)

  private val dittoActor: ActorRef[DittoCommand] = testKit.spawn(
    DittoActor(rootActorProbe.ref, messageBrokerActorProbe.ref, repositoryConfig, dittoConfig)
  )

  private val cartId: CartId = CartId(1).getOrElse(fail())
  private val store: Store = Store(1).getOrElse(fail())
  private val customer: Customer = Customer("mario@mail.com").getOrElse(fail())
  private val catalogItem: CatalogItem = CatalogItem(1).getOrElse(fail())
  private val itemId: ItemId = ItemId(1).getOrElse(fail())

  private def uri(hostName: String, port: String, namespace: String, cartId: CartId, store: Store): String =
    s"http://$hostName:$port/api/2/things/$namespace:cart-${cartId.value}-${store.value}"

  private case class DittoData(
    direction: String,
    messageSubject: String,
    cartId: CartId,
    store: Store,
    payload: Seq[(String, JsValue)]
  )

  private def parseDittoProtocol(namespace: String, message: String): Option[DittoData] = {
    val thingIdMatcher: Regex = (Pattern.quote(namespace) + ":cart-(?<cartId>[0-9]+)-(?<store>[0-9]+)").r
    message.parseJson.asJsObject.getFields("headers", "value") match {
      case Seq(headers, value) =>
        headers
          .asJsObject
          .getFields("ditto-message-direction", "ditto-message-subject", "ditto-message-thing-id") match {
            case Seq(JsString(direction), JsString(messageSubject), JsString(thingIdMatcher(cartId, store)))
                 if cartId.toLongOption.isDefined && store.toLongOption.isDefined =>
              (for {
                c <- CartId(cartId.toLong)
                s <- Store(store.toLong)
              } yield Some(DittoData(direction, messageSubject, c, s, value.asJsObject.fields.toSeq.sortBy(_._1))))
                .getOrElse(None)
            case _ => None
          }
      case Seq(headers) =>
        headers
          .asJsObject
          .getFields("ditto-message-direction", "ditto-message-subject", "ditto-message-thing-id") match {
            case Seq(JsString(direction), JsString(messageSubject), JsString(thingIdMatcher(cartId, store)))
                 if cartId.toLongOption.isDefined && store.toLongOption.isDefined =>
              (for {
                c <- CartId(cartId.toLong)
                s <- Store(store.toLong)
              } yield Some(DittoData(direction, messageSubject, c, s, Seq.empty))).getOrElse(None)
            case _ => None
          }
      case _ => None
    }
  }

  override def beforeAll(): Unit = {
    val latch: CountDownLatch = CountDownLatch(1)
    val (websocket, response): (UntypedActorRef, Future[WebSocketUpgradeResponse]) =
      Source
        .actorRef[Message](
          { case m: TextMessage.Strict if m.text === "SUCCESS" => CompletionStrategy.draining },
          { case m: TextMessage.Strict if m.text === "ERROR" => IllegalStateException() },
          bufferSize = 1,
          OverflowStrategy.dropTail
        )
        .viaMat(
          client.webSocketClientFlow(
            WebSocketRequest(
              s"ws://${dittoConfig.getString("hostName")}:${dittoConfig.getString("portNumber")}/ws/2",
              extraHeaders =
                Seq(Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password"))))
            )
          )
        )(Keep.both)
        .toMat(
          Flow[Message]
            .mapAsync(parallelism = 2) {
              case t: TextMessage => t.toStrict(60.seconds)
              case _ => Future.failed[TextMessage.Strict](IllegalArgumentException())
            }
            .mapConcat[DittoCommand](t =>
              if (t.text === "START-SEND-MESSAGES:ACK") {
                latch.countDown()
                List.empty
              } else {
                parseDittoProtocol(dittoConfig.getString("namespace"), t.text) match {
                  case Some(DittoData("TO", "associate", cartId, store, Seq("customer" -> JsString(customer)))) =>
                    Customer(customer).map(AssociateCart(cartId, store, _, responseProbe.ref) :: Nil).getOrElse(Nil)
                  case Some(DittoData("TO", "lock", cartId, store, Seq())) =>
                    LockCart(cartId, store, responseProbe.ref) :: Nil
                  case Some(DittoData("TO", "unlock", cartId, store, Seq())) =>
                    UnlockCart(cartId, store, responseProbe.ref) :: Nil
                  case Some(DittoData("TO", "raiseAlarm", cartId, store, Seq())) =>
                    RaiseCartAlarm(cartId, store) :: Nil
                  case _ => Nil
                }
              }
            )
            .to(Sink.foreach(serviceProbe ! _))
        )(Keep.left)
        .run()
    given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
    response
      .onComplete {
        case Failure(_) => fail()
        case Success(r) =>
          if (r.response.status === StatusCodes.SwitchingProtocols)
            websocket ! TextMessage("START-SEND-MESSAGES?namespaces=" + dittoConfig.getString("namespace"))
          else
            fail()
      }
    latch.await()
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())

  private def checkCartPresence(cartId: CartId, store: Store): Unit = {
    val latch: CountDownLatch = CountDownLatch(1)
    client
      .singleRequest(
        Get(
          uri(
            dittoConfig.getString("hostName"),
            dittoConfig.getString("portNumber"),
            dittoConfig.getString("namespace"),
            cartId,
            store
          )
        ).addHeader(
          Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
        )
      )
      .flatMap(r => Unmarshal(r.entity).to[String])
      .onComplete {
        case Failure(_) => fail()
        case Success(value) =>
          value.parseJson.asJsObject.getFields("definition", "attributes") match {
            case Seq(JsString(definition), attributes) =>
              definition shouldBe dittoConfig.getString("thingModel")
              attributes.asJsObject.getFields("id", "store", "movable") match {
                case Seq(JsNumber(c), JsNumber(s), JsBoolean(false)) if c.isValidLong && s.isValidLong =>
                  c.longValue shouldBe (cartId.value: Long)
                  s.longValue shouldBe (store.value: Long)
                  latch.countDown()
                case _ => fail()
              }
            case _ => fail()
          }
      }
    latch.await()
  }

  private def checkCartAbsence(): Unit = {
    val latch: CountDownLatch = CountDownLatch(1)
    client
      .singleRequest(
        Get(
          uri(
            dittoConfig.getString("hostName"),
            dittoConfig.getString("portNumber"),
            dittoConfig.getString("namespace"),
            cartId,
            store
          )
        ).addHeader(
          Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
        )
      )
      .onComplete {
        case Failure(_) => fail()
        case Success(r) =>
          r.status shouldBe StatusCodes.NotFound
          latch.countDown()
      }
    latch.await()
  }

  describe("A Ditto actor") {

    describe("when first started up") {
      ignore("should notify the root actor of its start") {
        rootActorProbe.expectMessage(60.seconds, Startup(true))
      }
    }

    describe("after being asked to move a cart when it is locked") {
      ignore("should send a message to itself raising the cart alarm") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        client
          .singleRequest(
            Post(
              uri(
                dittoConfig.getString("hostName"),
                dittoConfig.getString("portNumber"),
                dittoConfig.getString("namespace"),
                lockedCart.cartId,
                store
              ) + "/outbox/messages/cartMoved"
            ).addHeader(
              Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
            )
          )
        serviceProbe.expectMessage(60.seconds, RaiseCartAlarm(lockedCart.cartId, store))
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to move a cart when it is unlocked") {
      ignore("should do nothing") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        repository.update(UnlockedCart(lockedCart.cartId, store)).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        client
          .singleRequest(
            Post(
              uri(
                dittoConfig.getString("hostName"),
                dittoConfig.getString("portNumber"),
                dittoConfig.getString("namespace"),
                lockedCart.cartId,
                store
              ) + "/outbox/messages/cartMoved"
            ).addHeader(
              Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
            )
          )
        serviceProbe.expectNoMessage(60.seconds)
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to move a cart when it is associated") {
      ignore("should do nothing") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        repository.update(AssociatedCart(lockedCart.cartId, store, customer)).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        client
          .singleRequest(
            Post(
              uri(
                dittoConfig.getString("hostName"),
                dittoConfig.getString("portNumber"),
                dittoConfig.getString("namespace"),
                lockedCart.cartId,
                store
              ) + "/outbox/messages/cartMoved"
            ).addHeader(
              Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
            )
          )
        serviceProbe.expectNoMessage(60.seconds)
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to insert an item into a locked cart") {
      ignore("should send a message to itself raising the cart alarm") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        client
          .singleRequest(
            Post(
              uri(
                dittoConfig.getString("hostName"),
                dittoConfig.getString("portNumber"),
                dittoConfig.getString("namespace"),
                lockedCart.cartId,
                store
              ) + "/outbox/messages/itemInsertedIntoCart",
              JsObject(
                "catalogItem" -> catalogItem.toJson,
                "itemId" -> itemId.toJson
              )
            ).addHeader(
              Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
            )
          )
        serviceProbe.expectMessage(60.seconds, RaiseCartAlarm(lockedCart.cartId, store))
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to insert an item into an unlocked cart") {
      ignore("should send a message to itself raising the cart alarm") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        repository.update(UnlockedCart(lockedCart.cartId, store)).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        client
          .singleRequest(
            Post(
              uri(
                dittoConfig.getString("hostName"),
                dittoConfig.getString("portNumber"),
                dittoConfig.getString("namespace"),
                lockedCart.cartId,
                store
              ) + "/outbox/messages/itemInsertedIntoCart",
              JsObject(
                "catalogItem" -> catalogItem.toJson,
                "itemId" -> itemId.toJson
              )
            ).addHeader(
              Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
            )
          )
        serviceProbe.expectMessage(60.seconds, RaiseCartAlarm(lockedCart.cartId, store))
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to insert an item into an associated cart") {
      ignore("should send a message to the message broker actor") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        repository.update(AssociatedCart(lockedCart.cartId, store, customer)).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        client
          .singleRequest(
            Post(
              uri(
                dittoConfig.getString("hostName"),
                dittoConfig.getString("portNumber"),
                dittoConfig.getString("namespace"),
                lockedCart.cartId,
                store
              ) + "/outbox/messages/itemInsertedIntoCart",
              JsObject(
                "catalogItem" -> catalogItem.toJson,
                "itemId" -> itemId.toJson
              )
            ).addHeader(
              Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
            )
          )
        messageBrokerActorProbe.expectMessage(
          60.seconds,
          ItemAddedToCart(ItemAddedToCartEvent(customer, store, catalogItem, itemId))
        )
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
        repository.remove(lockedCart).getOrElse(fail())
      }
    }
  }

  describe("A locked cart") {
    describe("after being added as a digital twin") {
      ignore("should be present in the Ditto service") {
        dittoActor ! AddCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(cartId, store)
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
      }
    }

    describe("if never added as a digital twin") {
      ignore("should not be present in the Ditto service") {
        checkCartAbsence()
      }

      ignore("should not be removable") {
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        checkCartAbsence()
        responseProbe.expectMessage(60.seconds, Left[ValidationError, Unit](CartNotFound))
      }
    }

    describe("after being unlocked") {
      ignore("should show as unlocked in the Ditto service") {
        dittoActor ! AddCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(cartId, store)
        dittoActor ! UnlockCart(cartId, store, responseProbe.ref)
        serviceProbe.expectMessage(60.seconds, UnlockCart(cartId, store, responseProbe.ref))
        responseProbe.expectMessage(5.minutes, Left[ValidationError, Unit](DittoError))
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
      }
    }

    describe("after being associated to a customer") {
      ignore("should show the associated customer in the Ditto service") {
        dittoActor ! AddCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(cartId, store)
        dittoActor ! AssociateCart(cartId, store, customer, responseProbe.ref)
        serviceProbe.expectMessage(60.seconds, AssociateCart(cartId, store, customer, responseProbe.ref))
        responseProbe.expectMessage(5.minutes, Left[ValidationError, Unit](DittoError))
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
      }
    }

    describe("after being associated then locked again") {
      ignore("should show as locked in the Ditto service") {
        dittoActor ! AddCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(cartId, store)
        dittoActor ! AssociateCart(cartId, store, customer, responseProbe.ref)
        serviceProbe.expectMessage(60.seconds, AssociateCart(cartId, store, customer, responseProbe.ref))
        responseProbe.expectMessage(5.minutes, Left[ValidationError, Unit](DittoError))
        dittoActor ! LockCart(cartId, store, responseProbe.ref)
        serviceProbe.expectMessage(60.seconds, LockCart(cartId, store, responseProbe.ref))
        responseProbe.expectMessage(5.minutes, Left[ValidationError, Unit](DittoError))
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence()
      }
    }
  }
}
