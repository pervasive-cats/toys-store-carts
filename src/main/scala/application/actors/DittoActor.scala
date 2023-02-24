/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.util.concurrent.ForkJoinPool
import java.util.regex.Pattern

import scala.concurrent.*
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.matching.Regex

import akka.Done
import akka.NotUsed
import akka.actor.ActorRef as UntypedActorRef
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.client.RequestBuilding.Delete
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.client.RequestBuilding.Put
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.ws.*
import akka.stream.CompletionStrategy
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.enrichAny
import spray.json.enrichString

import application.actors.DittoCommand.*
import application.actors.RootCommand.Startup
import application.Serializers.given
import carts.cart.domainevents.{CartMoved as CartMovedEvent, ItemInsertedIntoCart as ItemInsertedIntoCartEvent}
import carts.cart.services.{CartMovementHandlers, ItemInsertionHandlers}
import carts.cart.valueobjects.{CartId, Store}
import carts.cart.valueobjects.item.{CatalogItem, ItemId}
import carts.cart.Repository
import AnyOps.===
import carts.cart.Repository.CartNotFound

object DittoActor extends SprayJsonSupport {

  case object DittoError extends ValidationError {

    override val message: String = "An error with the Ditto service was encountered."
  }

  private def handleResponse(
    response: Future[HttpResponse],
    replyTo: ActorRef[Validated[Unit]],
    executionContext: ExecutionContext
  ): Behavior[DittoCommand] = {
    response
      .onComplete {
        case Failure(_) => replyTo ! Left[ValidationError, Unit](DittoError)
        case Success(response) =>
          response.status match {
            case StatusCodes.OK | StatusCodes.Created | StatusCodes.Accepted | StatusCodes.NoContent =>
              replyTo ! Right[ValidationError, Unit](())
            case StatusCodes.NotFound => replyTo ! Left[ValidationError, Unit](CartNotFound)
            case _ => replyTo ! Left[ValidationError, Unit](DittoError)
          }
      }(executionContext)
    Behaviors.same[DittoCommand]
  }

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

  def apply(
    root: ActorRef[RootCommand],
    messageBrokerActor: ActorRef[MessageBrokerCommand],
    repositoryConfig: Config,
    dittoConfig: Config
  ): Behavior[DittoCommand] =
    Behaviors.setup[DittoCommand] { ctx =>
      val client: HttpExt = Http()(ctx.system.classicSystem)
      given ActorSystem = ctx.system.classicSystem
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
                case t: TextMessage => t.toStrict(30.seconds)
                case _ => Future.failed[TextMessage.Strict](IllegalArgumentException())
              }
              .mapConcat[DittoCommand](t =>
                if (t.text === "START-SEND-MESSAGES:ACK") {
                  DittoMessagesIncoming :: Nil
                } else {
                  parseDittoProtocol(dittoConfig.getString("namespace"), t.text) match {
                    case Some(
                           DittoData(
                             "FROM",
                             "itemInsertedIntoCart",
                             cartId,
                             store,
                             Seq("catalogItem" -> JsNumber(catalogItem), "itemId" -> JsNumber(itemId))
                           )
                         ) if catalogItem.isValidLong && itemId.isValidLong =>
                      (for {
                        k <- CatalogItem(catalogItem.longValue)
                        i <- ItemId(itemId.longValue)
                      } yield ItemInsertedIntoCart(cartId, store, k, i) :: Nil).getOrElse(Nil)
                    case Some(DittoData("FROM", "cartMoved", cartId, store, Seq())) =>
                      CartMoved(cartId, store) :: Nil
                    case _ => Nil
                  }
                }
              )
              .to(Sink.foreach(ctx.self ! _))
          )(Keep.left)
          .run()
      given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
      response
        .onComplete {
          case Failure(_) => root ! Startup(success = false)
          case Success(r) =>
            if (r.response.status === StatusCodes.SwitchingProtocols)
              ctx.self ! WebsocketConnected
            else
              root ! Startup(success = false)
        }
      Behaviors.receiveMessage {
        case WebsocketConnected =>
          websocket ! TextMessage("START-SEND-MESSAGES?namespaces=" + dittoConfig.getString("namespace"))
          Behaviors.receiveMessage {
            case DittoMessagesIncoming => onDittoMessagesIncoming(root, client, messageBrokerActor, repositoryConfig, dittoConfig)
            case _ => Behaviors.unhandled
          }
        case _ => Behaviors.unhandled[DittoCommand]
      }
    }

  private def onDittoMessagesIncoming(
    root: ActorRef[RootCommand],
    client: HttpExt,
    messageBrokerActor: ActorRef[MessageBrokerCommand],
    repositoryConfig: Config,
    dittoConfig: Config
  )(
    using
    ExecutionContext
  ): Behavior[DittoCommand] =
    root ! Startup(success = true)
    Behaviors.receive { (ctx, msg) =>
      val itemInsertionHandlers: ItemInsertionHandlers = ItemInsertionHandlers(messageBrokerActor, ctx.self)
      val cartMovementHandlers: CartMovementHandlers = CartMovementHandlers(ctx.self)
      given Repository = Repository(repositoryConfig)
      msg match {
        case AddCart(cartId, store, replyTo) =>
          handleResponse(
            client
              .singleRequest(
                Put(
                  uri(
                    dittoConfig.getString("hostName"),
                    dittoConfig.getString("portNumber"),
                    dittoConfig.getString("namespace"),
                    cartId,
                    store
                  ),
                  JsObject(
                    "definition" -> dittoConfig.getString("thingModel").toJson,
                    "attributes" -> JsObject(
                      "id" -> cartId.toJson,
                      "store" -> store.toJson,
                      "movable" -> false.toJson
                    )
                  )
                ).addHeader(
                  Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
                )
              ),
            replyTo,
            ctx.executionContext
          )
        case RemoveCart(cartId, store, replyTo) =>
          handleResponse(
            client
              .singleRequest(
                Delete(
                  uri(
                    dittoConfig.getString("hostName"),
                    dittoConfig.getString("portNumber"),
                    dittoConfig.getString("namespace"),
                    cartId,
                    store
                  )
                )
                  .addHeader(
                    Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
                  )
              )
              .flatMap(r =>
                r.status match {
                  case StatusCodes.NoContent =>
                    client.singleRequest(
                      Delete(
                        s"http://${dittoConfig.getString("hostName")}:${dittoConfig.getString("portNumber")}"
                        + s"/api/2/policies/${dittoConfig.getString("namespace")}:cart-${cartId.value}-${store.value}"
                      ).addHeader(
                        Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
                      )
                    )
                  case _ => Future.successful(r)
                }
              ),
            replyTo,
            ctx.executionContext
          )
        case RaiseCartAlarm(cartId, store) =>
          client
            .singleRequest(
              Post(
                uri(
                  dittoConfig.getString("hostName"),
                  dittoConfig.getString("portNumber"),
                  dittoConfig.getString("namespace"),
                  cartId,
                  store
                ) + "/inbox/messages/raiseAlarm"
              )
                .addHeader(
                  Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
                )
            )
          Behaviors.same[DittoCommand]
        case AssociateCart(cartId, store, customer, replyTo) =>
          handleResponse(
            client
              .singleRequest(
                Post(
                  uri(
                    dittoConfig.getString("hostName"),
                    dittoConfig.getString("portNumber"),
                    dittoConfig.getString("namespace"),
                    cartId,
                    store
                  ) + "/inbox/messages/associate",
                  JsObject("customer" -> customer.toJson)
                )
                  .addHeader(
                    Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
                  )
              ),
            replyTo,
            ctx.executionContext
          )
        case UnlockCart(cartId, store, replyTo) =>
          handleResponse(
            client
              .singleRequest(
                Post(
                  uri(
                    dittoConfig.getString("hostName"),
                    dittoConfig.getString("portNumber"),
                    dittoConfig.getString("namespace"),
                    cartId,
                    store
                  ) + "/inbox/messages/unlock"
                )
                  .addHeader(
                    Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
                  )
              ),
            replyTo,
            ctx.executionContext
          )
        case LockCart(cartId, store, replyTo) =>
          handleResponse(
            client
              .singleRequest(
                Post(
                  uri(
                    dittoConfig.getString("hostName"),
                    dittoConfig.getString("portNumber"),
                    dittoConfig.getString("namespace"),
                    cartId,
                    store
                  ) + "/inbox/messages/lock"
                )
                  .addHeader(
                    Authorization(BasicHttpCredentials(dittoConfig.getString("username"), dittoConfig.getString("password")))
                  )
              ),
            replyTo,
            ctx.executionContext
          )
        case ItemInsertedIntoCart(cartId, store, catalogItem, itemId) =>
          itemInsertionHandlers.onItemInsertedIntoCart(ItemInsertedIntoCartEvent(cartId, store, catalogItem, itemId))
          Behaviors.same[DittoCommand]
        case CartMoved(cartId, store) =>
          cartMovementHandlers.onCartMoved(CartMovedEvent(cartId, store))
          Behaviors.same[DittoCommand]
        case _ => Behaviors.unhandled[DittoCommand]
      }
    }
}
