/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.OptionConverters.RichOptional
import scala.util.Failure
import scala.util.Success
import scala.util.matching.Regex

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import eu.timepit.refined.auto.autoUnwrap
import org.eclipse.ditto.base.model.common.HttpStatus
import org.eclipse.ditto.client.DittoClient
import org.eclipse.ditto.client.DittoClients
import org.eclipse.ditto.client.configuration.BasicAuthenticationConfiguration
import org.eclipse.ditto.client.configuration.WebSocketMessagingConfiguration
import org.eclipse.ditto.client.live.messages.MessageSender
import org.eclipse.ditto.client.live.messages.RepliableMessage
import org.eclipse.ditto.client.messaging.AuthenticationProviders
import org.eclipse.ditto.client.messaging.MessagingProviders
import org.eclipse.ditto.client.options.Options
import org.eclipse.ditto.json.JsonObject
import org.eclipse.ditto.messages.model.MessageDirection
import org.eclipse.ditto.things.model.Thing
import org.eclipse.ditto.things.model.ThingId
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.DoNotDiscover
import org.scalatest.OptionValues.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.enrichAny
import spray.json.enrichString

import application.actors.DittoCommand.*
import application.actors.MessageBrokerCommand.ItemAddedToCart
import application.actors.RootCommand.Startup
import application.routes.entities.Response.*
import application.Serializers.given
import application.actors.DittoActor.DittoError
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity}
import carts.cart.valueobjects.*
import carts.cart.Repository.CartNotFound
import carts.cart.valueobjects.item.{CatalogItem, ItemId}
import carts.cart.Repository
import carts.cart.domainevents.{ItemInsertedIntoCart, ItemAddedToCart as ItemAddedToCartEvent}
import carts.cart.entities.* //scalafix:ok

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

  private val repository: Repository = Repository(repositoryConfig)

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var maybeClient: Option[DittoClient] = None

  private val dittoActor: ActorRef[DittoCommand] = testKit.spawn(
    DittoActor(rootActorProbe.ref, messageBrokerActorProbe.ref, repositoryConfig, dittoConfig)
  )

  private val cartId: CartId = CartId(1).getOrElse(fail())
  private val store: Store = Store(1).getOrElse(fail())
  private val customer: Customer = Customer("mario@mail.com").getOrElse(fail())
  private val catalogItem: CatalogItem = CatalogItem(1).getOrElse(fail())
  private val itemId: ItemId = ItemId(1).getOrElse(fail())

  private def sendReply(
    message: RepliableMessage[String, String],
    correlationId: String,
    status: HttpStatus,
    payload: String
  ): Unit = message.reply().httpStatus(status).correlationId(correlationId).payload(payload).send()

  private def handleMessage(
    message: RepliableMessage[String, String],
    messageHandler: (RepliableMessage[String, String], CartId, Store, String, Seq[JsValue]) => Unit,
    payloadFields: String*
  ): Unit = {
    val thingIdMatcher: Regex = "cart-(?<cartId>[0-9]+)-(?<store>[0-9]+)".r
    (message.getDirection, message.getEntityId.getName, message.getCorrelationId.toScala) match {
      case (MessageDirection.TO, thingIdMatcher(cartId, store), Some(correlationId))
           if cartId.toLongOption.isDefined && store.toLongOption.isDefined =>
        (for {
          c <- CartId(cartId.toLong)
          s <- Store(store.toLong)
        } yield (c, s)).fold(
          error => sendReply(message, correlationId, HttpStatus.BAD_REQUEST, ErrorResponseEntity(error).toJson.compactPrint),
          (c, s) =>
            messageHandler(
              message,
              c,
              s,
              correlationId,
              message.getPayload.toScala.map(_.parseJson.asJsObject.getFields(payloadFields: _*)).getOrElse(Seq.empty[JsValue])
            )
        )
      case _ => ()
    }
  }

  override def beforeAll(): Unit = {
    val disconnectedDittoClient = DittoClients.newInstance(
      MessagingProviders.webSocket(
        WebSocketMessagingConfiguration
          .newBuilder
          .endpoint(s"ws://${dittoConfig.getString("hostName")}:${dittoConfig.getString("portNumber")}/ws/2")
          .connectionErrorHandler(_ => fail())
          .build,
        AuthenticationProviders.basic(
          BasicAuthenticationConfiguration
            .newBuilder
            .username(dittoConfig.getString("username"))
            .password(dittoConfig.getString("password"))
            .build
        )
      )
    )
    val client: DittoClient =
      disconnectedDittoClient
        .connect
        .exceptionally { _ =>
          disconnectedDittoClient.destroy()
          fail()
        }
        .toCompletableFuture
        .get()
    client
      .live
      .startConsumption(
        Options.Consumption.namespaces(dittoConfig.getString("namespace"))
      )
      .exceptionally { _ =>
        disconnectedDittoClient.destroy()
        fail()
      }
      .toCompletableFuture
      .get()
    client
      .live
      .registerForMessage[String, String](
        "ditto_actor_associate",
        "associate",
        classOf[String],
        (msg: RepliableMessage[String, String]) =>
          handleMessage(
            msg,
            (msg, cartId, store, correlationId, fields) =>
              fields match {
                case Seq(JsString(customer)) =>
                  Customer(customer)
                    .map(serviceProbe ! AssociateCart(cartId, store, _, responseProbe.ref))
                    .fold(
                      e =>
                        sendReply(
                          msg,
                          correlationId,
                          HttpStatus.BAD_REQUEST,
                          ErrorResponseEntity(e).toJson.compactPrint
                        ),
                      _ => sendReply(msg, correlationId, HttpStatus.OK, ResultResponseEntity(()).toJson.compactPrint)
                    )
                case _ =>
                  sendReply(
                    msg,
                    correlationId,
                    HttpStatus.BAD_REQUEST,
                    ErrorResponseEntity(DittoError).toJson.compactPrint
                  )
              },
            "customer"
          )
      )
    client
      .live
      .registerForMessage(
        "ditto_actor_lock",
        "lock",
        classOf[String],
        (msg: RepliableMessage[String, String]) =>
          handleMessage(
            msg,
            (msg, cartId, store, correlationId, _) => {
              serviceProbe ! LockCart(cartId, store, responseProbe.ref)
              sendReply(
                msg,
                correlationId,
                HttpStatus.OK,
                ResultResponseEntity(()).toJson.compactPrint
              )
            }
          )
      )
    client
      .live
      .registerForMessage(
        "ditto_actor_unlock",
        "unlock",
        classOf[String],
        (msg: RepliableMessage[String, String]) =>
          handleMessage(
            msg,
            (msg, cartId, store, correlationId, _) => {
              serviceProbe ! UnlockCart(cartId, store, responseProbe.ref)
              sendReply(
                msg,
                correlationId,
                HttpStatus.OK,
                ResultResponseEntity(()).toJson.compactPrint
              )
            }
          )
      )
    client
      .live
      .registerForMessage(
        "ditto_actor_raiseAlarm",
        "raiseAlarm",
        classOf[String],
        (msg: RepliableMessage[String, String]) =>
          handleMessage(
            msg,
            (msg, cartId, store, correlationId, _) => {
              serviceProbe ! RaiseCartAlarm(cartId, store)
              sendReply(
                msg,
                correlationId,
                HttpStatus.OK,
                ResultResponseEntity(()).toJson.compactPrint
              )
            }
          )
      )
    maybeClient = Some(client)
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private def thingId(cartId: CartId, store: Store): ThingId =
    ThingId.of(s"${dittoConfig.getString("namespace")}:cart-${cartId.value}-${store.value}")

  private def checkCartPresence(cartId: CartId, store: Store): Unit = {
    val list =
      maybeClient
        .getOrElse(fail())
        .twin()
        .retrieve(thingId(cartId, store))
        .toCompletableFuture
        .get()
    if (list.size() !== 1)
      fail()
    val thing: Thing = list.get(0)
    (thing.getDefinition.toScala, thing.getAttributes.toScala) match {
      case (Some(definition), Some(attributes)) =>
        definition.getUrl.toScala.value.toExternalForm shouldBe dittoConfig.getString("thingModel")
        (
          attributes.getValue("id").toScala,
          attributes.getValue("store").toScala,
          attributes.getValue("movable").toScala
        ) match {
          case (Some(c), Some(s), Some(m)) if c.isLong && s.isLong && m.isBoolean =>
            c.asLong() shouldBe (cartId.value: Long)
            s.asLong() shouldBe (store.value: Long)
            m.asBoolean() shouldBe false
          case _ => fail()
        }
      case _ => fail()
    }
  }

  private def checkCartAbsence(cartId: CartId, store: Store): Unit =
    try {
      maybeClient
        .getOrElse(fail())
        .twin()
        .retrieve(thingId(cartId, store))
        .toCompletableFuture
        .get()
    } catch {
      case e: CompletionException =>
        e.getCause match {
          case e: ThingNotAccessibleException if e.getHttpStatus === HttpStatus.NOT_FOUND => ()
          case _ => fail()
        }
    }

  describe("A Ditto actor") {

    describe("when first started up") {
      it("should notify the root actor of its start") {
        rootActorProbe.expectMessage(60.seconds, Startup(true))
      }
    }

    describe("after being asked to move a cart when it is locked") {
      it("should send a message to itself raising the cart alarm") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        maybeClient
          .getOrElse(fail())
          .live()
          .forId(thingId(lockedCart.cartId, store))
          .message[JsonObject]()
          .from()
          .subject("cartMoved")
          .send()
        serviceProbe.expectMessage(60.seconds, RaiseCartAlarm(lockedCart.cartId, store))
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(lockedCart.cartId, store)
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to move a cart when it is unlocked") {
      it("should do nothing") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        repository.update(UnlockedCart(lockedCart.cartId, store)).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        maybeClient
          .getOrElse(fail())
          .live()
          .forId(thingId(lockedCart.cartId, store))
          .message[JsonObject]()
          .from()
          .subject("cartMoved")
          .send()
        serviceProbe.expectNoMessage(60.seconds)
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(lockedCart.cartId, store)
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to move a cart when it is associated") {
      it("should do nothing") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        repository.update(AssociatedCart(lockedCart.cartId, store, customer)).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        maybeClient
          .getOrElse(fail())
          .live()
          .forId(thingId(lockedCart.cartId, store))
          .message[JsonObject]()
          .from()
          .subject("cartMoved")
          .send()
        serviceProbe.expectNoMessage(60.seconds)
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(lockedCart.cartId, store)
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to insert an item into a locked cart") {
      it("should send a message to itself raising the cart alarm") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        maybeClient
          .getOrElse(fail())
          .live()
          .forId(thingId(lockedCart.cartId, store))
          .message()
          .from()
          .subject("itemInsertedIntoCart")
          .payload(
            JsonObject.of(
              JsObject(
                "catalogItem" -> catalogItem.toJson,
                "itemId" -> itemId.toJson
              ).compactPrint
            )
          )
          .send()
        serviceProbe.expectMessage(60.seconds, RaiseCartAlarm(lockedCart.cartId, store))
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(lockedCart.cartId, store)
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to insert an item into an unlocked cart") {
      it("should send a message to itself raising the cart alarm") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        repository.update(UnlockedCart(lockedCart.cartId, store)).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        maybeClient
          .getOrElse(fail())
          .live()
          .forId(thingId(lockedCart.cartId, store))
          .message()
          .from()
          .subject("itemInsertedIntoCart")
          .payload(
            JsonObject.of(
              JsObject(
                "catalogItem" -> catalogItem.toJson,
                "itemId" -> itemId.toJson
              ).compactPrint
            )
          )
          .send()
        serviceProbe.expectMessage(60.seconds, RaiseCartAlarm(lockedCart.cartId, store))
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(lockedCart.cartId, store)
        repository.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being asked to insert an item into an associated cart") {
      it("should send a message to the message broker actor") {
        val lockedCart: LockedCart = repository.add(store).getOrElse(fail())
        repository.update(AssociatedCart(lockedCart.cartId, store, customer)).getOrElse(fail())
        dittoActor ! AddCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(lockedCart.cartId, store)
        maybeClient
          .getOrElse(fail())
          .live()
          .forId(thingId(lockedCart.cartId, store))
          .message()
          .from()
          .subject("itemInsertedIntoCart")
          .payload(
            JsonObject.of(
              JsObject(
                "catalogItem" -> catalogItem.toJson,
                "itemId" -> itemId.toJson
              ).compactPrint
            )
          )
          .send()
        messageBrokerActorProbe.expectMessage(
          60.seconds,
          ItemAddedToCart(ItemAddedToCartEvent(customer, store, catalogItem, itemId))
        )
        dittoActor ! RemoveCart(lockedCart.cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(lockedCart.cartId, store)
        repository.remove(lockedCart).getOrElse(fail())
      }
    }
  }

  describe("A locked cart") {
    describe("after being added as a digital twin") {
      it("should be present in the Ditto service") {
        dittoActor ! AddCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(cartId, store)
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(cartId, store)
      }
    }

    describe("if never added as a digital twin") {
      it("should not be present in the Ditto service") {
        checkCartAbsence(cartId, store)
      }

      it("should not be removable") {
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        checkCartAbsence(cartId, store)
        responseProbe.expectMessage(60.seconds, Left[ValidationError, Unit](CartNotFound))
      }
    }

    describe("after being unlocked") {
      it("should show as unlocked in the Ditto service") {
        dittoActor ! AddCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(cartId, store)
        dittoActor ! UnlockCart(cartId, store, responseProbe.ref)
        serviceProbe.expectMessage(60.seconds, UnlockCart(cartId, store, responseProbe.ref))
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(cartId, store)
      }
    }

    describe("after being associated to a customer") {
      it("should show the associated customer in the Ditto service") {
        dittoActor ! AddCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(cartId, store)
        dittoActor ! AssociateCart(cartId, store, customer, responseProbe.ref)
        serviceProbe.expectMessage(60.seconds, AssociateCart(cartId, store, customer, responseProbe.ref))
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(cartId, store)
      }
    }

    describe("after being associated then locked again") {
      it("should show as locked in the Ditto service") {
        dittoActor ! AddCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartPresence(cartId, store)
        dittoActor ! AssociateCart(cartId, store, customer, responseProbe.ref)
        serviceProbe.expectMessage(60.seconds, AssociateCart(cartId, store, customer, responseProbe.ref))
        responseProbe.expectMessage(5.minutes, Right[ValidationError, Unit](()))
        dittoActor ! LockCart(cartId, store, responseProbe.ref)
        serviceProbe.expectMessage(60.seconds, LockCart(cartId, store, responseProbe.ref))
        responseProbe.expectMessage(5.minutes, Right[ValidationError, Unit](()))
        dittoActor ! RemoveCart(cartId, store, responseProbe.ref)
        responseProbe.expectMessage(60.seconds, Right[ValidationError, Unit](()))
        checkCartAbsence(cartId, store)
      }
    }
  }
}
