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
import akka.actor.{ActorRef => UntypedActorRef}
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
    s"http://$hostName:$port/api/2/things/$namespace:cart-$cartId-$store"

  def apply(
    root: ActorRef[RootCommand],
    messageBrokerActor: ActorRef[MessageBrokerCommand],
    repositoryConfig: Config,
    dittoConfig: Config
  ): Behavior[DittoCommand] = {
    Behaviors.setup[DittoCommand] { ctx =>
      val topicMatcher: Regex =
        (Pattern.quote(dittoConfig.getString("namespace"))
          + "/cart-(?<cartId>[0-9]+)-(?<store>[0-9]+)/things/twin/messages/(?<messageSubject>[a-zA-Z]+)").r
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
                  ctx.self ! DittoMessagesIncoming
                  Nil
                } else {
                  t.text.parseJson.asJsObject.getFields("topic", "value") match {
                    case Seq(JsString(topic), value) =>
                      topic match {
                        case topicMatcher(cartId, store, messageSubject)
                             if cartId.toLongOption.isDefined && store.toLongOption.isDefined =>
                          (messageSubject, value.asJsObject.fields.toSeq.sortBy(_._1)) match {
                            case (
                                   "itemInsertedIntoCart",
                                   Seq("catalogItem" -> JsNumber(catalogItem), "itemId" -> JsNumber(itemId))
                                 ) if catalogItem.isValidLong && itemId.isValidLong =>
                              (for {
                                c <- CartId(cartId.toLong)
                                s <- Store(store.toLong)
                                k <- CatalogItem(catalogItem.longValue)
                                i <- ItemId(itemId.longValue)
                              } yield ItemInsertedIntoCart(c, s, k, i) :: Nil).getOrElse(Nil)
                            case ("cartMoved", Seq()) =>
                              (for {
                                c <- CartId(cartId.toLong)
                                s <- Store(store.toLong)
                              } yield CartMoved(c, s) :: Nil).getOrElse(Nil)
                          }
                        case _ => Nil
                      }
                    case _ => Nil
                  }
                }
              )
              .map(ctx.self ! _)
              .to(Sink.ignore)
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
                    "definition" -> "https://raw.githubusercontent.com/pervasive-cats/toys-store-carts/main/cart.jsonld".toJson,
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
