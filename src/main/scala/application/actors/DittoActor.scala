/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.net.http.HttpHeaders
import java.util.concurrent.CompletionException
import java.util.concurrent.ForkJoinPool
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.regex.Pattern
import javax.sql.DataSource

import scala.concurrent.*
import scala.concurrent.duration.DurationInt
import scala.jdk.OptionConverters.RichOptional
import scala.util.Failure
import scala.util.Success
import scala.util.matching.Regex

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.typesafe.config.Config
import eu.timepit.refined.auto.autoUnwrap
import io.getquill.JdbcContextConfig
import org.eclipse.ditto.base.model.common.HttpStatus
import org.eclipse.ditto.client.DittoClient
import org.eclipse.ditto.client.DittoClients
import org.eclipse.ditto.client.configuration.*
import org.eclipse.ditto.client.live.commands.LiveCommandHandler
import org.eclipse.ditto.client.live.messages.MessageSender
import org.eclipse.ditto.client.live.messages.RepliableMessage
import org.eclipse.ditto.client.messaging.AuthenticationProviders
import org.eclipse.ditto.client.messaging.MessagingProviders
import org.eclipse.ditto.client.options.Options
import org.eclipse.ditto.json.JsonObject
import org.eclipse.ditto.messages.model.Message as DittoMessage
import org.eclipse.ditto.messages.model.MessageDirection
import org.eclipse.ditto.policies.model.PolicyId
import org.eclipse.ditto.things.model.*
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException
import spray.json.JsNumber
import spray.json.JsObject
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
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity}
import carts.cart.Repository.CartNotFound

object DittoActor extends SprayJsonSupport {

  case object DittoError extends ValidationError {

    override val message: String = "An error with the Ditto service was encountered."
  }

  private def sendReply(
    message: RepliableMessage[String, String],
    correlationId: String,
    status: HttpStatus,
    payload: Option[String]
  ): Unit = {
    val msg: MessageSender.SetPayloadOrSend[String] = message.reply().httpStatus(status).correlationId(correlationId)
    payload match {
      case Some(p) => msg.payload(p)
      case None => ()
    }
    msg.send()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Null", "scalafix:DisableSyntax.null"))
  private def responseHandler[T]: ActorRef[Validated[Unit]] => BiConsumer[T, Throwable] =
    r =>
      (_, t) =>
        if (t === null)
          r ! Right[ValidationError, Unit](())
        else
          t.getCause match {
            case e: ThingNotAccessibleException if e.getHttpStatus === HttpStatus.NOT_FOUND =>
              r ! Left[ValidationError, Unit](CartNotFound)
            case _ => r ! Left[ValidationError, Unit](DittoError)
          }

  private def sendMessage(
    client: DittoClient,
    namespace: String,
    cartId: CartId,
    store: Store,
    subject: String,
    payload: Option[JsonObject],
    replyTo: Option[ActorRef[Validated[Unit]]]
  ): Unit = {
    val message: MessageSender.SetPayloadOrSend[JsonObject] =
      client
        .live()
        .forId(ThingId.of(s"$namespace:cart-${cartId.value}-${store.value}"))
        .message()
        .to()
        .subject(subject)
    (payload, replyTo) match {
      case (Some(p), Some(r)) => message.payload(p).send(classOf[String], responseHandler(r))
      case (None, Some(r)) => message.send(classOf[String], responseHandler(r))
      case (Some(p), None) => message.payload(p).send()
      case _ => message.send()
    }
  }

  private def handleMessage(
    message: RepliableMessage[String, String],
    messageHandler: (RepliableMessage[String, String], CartId, Store, String, Seq[JsValue]) => Unit,
    payloadFields: String*
  ): Unit = {
    val thingIdMatcher: Regex = "cart-(?<cartId>[0-9]+)-(?<store>[0-9]+)".r
    (message.getDirection, message.getEntityId.getName, message.getCorrelationId.toScala) match {
      case (MessageDirection.FROM, thingIdMatcher(cartId, store), Some(correlationId))
           if cartId.toLongOption.isDefined && store.toLongOption.isDefined =>
        (for {
          c <- CartId(cartId.toLong)
          s <- Store(store.toLong)
        } yield (c, s)).fold(
          error =>
            sendReply(message, correlationId, HttpStatus.BAD_REQUEST, Some(ErrorResponseEntity(error).toJson.compactPrint)),
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

  @SuppressWarnings(Array("org.wartremover.warts.Null", "scalafix:DisableSyntax.null"))
  def apply(
    root: ActorRef[RootCommand],
    messageBrokerActor: ActorRef[MessageBrokerCommand],
    dataSource: DataSource,
    dittoConfig: Config
  ): Behavior[DittoCommand] =
    Behaviors.setup[DittoCommand] { ctx =>
      val disconnectedDittoClient = DittoClients.newInstance(
        MessagingProviders.webSocket(
          WebSocketMessagingConfiguration
            .newBuilder
            .endpoint(s"ws://${dittoConfig.getString("hostName")}:${dittoConfig.getString("portNumber")}/ws/2")
            .connectionErrorHandler(_ => root ! Startup(success = false))
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
      disconnectedDittoClient
        .connect
        .thenAccept(ctx.self ! DittoClientConnected(_))
        .exceptionally { _ =>
          disconnectedDittoClient.destroy()
          root ! Startup(success = false)
          null
        }
      Behaviors.receiveMessage {
        case DittoClientConnected(client) =>
          client
            .live
            .startConsumption(
              Options.Consumption.namespaces(dittoConfig.getString("namespace"))
            )
            .thenRun(() => ctx.self ! DittoMessagesIncoming)
            .exceptionally { _ =>
              disconnectedDittoClient.destroy()
              root ! Startup(success = false)
              null
            }
          Behaviors.receiveMessage {
            case DittoMessagesIncoming =>
              client
                .live
                .registerForMessage[String, String](
                  "ditto_actor_itemInsertedIntoCart",
                  "itemInsertedIntoCart",
                  classOf[String],
                  (msg: RepliableMessage[String, String]) =>
                    handleMessage(
                      msg,
                      (msg, cartId, store, correlationId, fields) =>
                        fields match {
                          case Seq(JsNumber(catalogItem), JsNumber(itemId)) if catalogItem.isValidLong && itemId.isValidLong =>
                            (for {
                              k <- CatalogItem(catalogItem.longValue)
                              i <- ItemId(itemId.longValue)
                            } yield ctx.self ! ItemInsertedIntoCart(cartId, store, k, i)).fold(
                              e =>
                                sendReply(
                                  msg,
                                  correlationId,
                                  HttpStatus.BAD_REQUEST,
                                  Some(ErrorResponseEntity(e).toJson.compactPrint)
                                ),
                              _ =>
                                sendReply(msg, correlationId, HttpStatus.OK, Some(ResultResponseEntity(()).toJson.compactPrint))
                            )
                          case _ =>
                            sendReply(
                              msg,
                              correlationId,
                              HttpStatus.BAD_REQUEST,
                              Some(ErrorResponseEntity(DittoError).toJson.compactPrint)
                            )
                        },
                      "catalogItem",
                      "itemId"
                    )
                )
              client
                .live
                .registerForMessage(
                  "ditto_actor_cartMoved",
                  "cartMoved",
                  classOf[String],
                  (msg: RepliableMessage[String, String]) =>
                    handleMessage(
                      msg,
                      (msg, cartId, store, correlationId, _) => {
                        ctx.self ! CartMoved(cartId, store)
                        sendReply(
                          msg,
                          correlationId,
                          HttpStatus.OK,
                          Some(ResultResponseEntity(()).toJson.compactPrint)
                        )
                      }
                    )
                )
              onDittoMessagesIncoming(root, client, messageBrokerActor, dataSource, dittoConfig)
            case _ => Behaviors.unhandled[DittoCommand]
          }
        case _ => Behaviors.unhandled[DittoCommand]
      }
    }

  private def onDittoMessagesIncoming(
    root: ActorRef[RootCommand],
    client: DittoClient,
    messageBrokerActor: ActorRef[MessageBrokerCommand],
    dataSource: DataSource,
    dittoConfig: Config
  ): Behavior[DittoCommand] = {
    root ! Startup(success = true)
    Behaviors.receive { (ctx, msg) =>
      val itemInsertionHandlers: ItemInsertionHandlers = ItemInsertionHandlers(messageBrokerActor, ctx.self)
      val cartMovementHandlers: CartMovementHandlers = CartMovementHandlers(ctx.self)
      given Repository = Repository(dataSource)
      msg match {
        case AddCart(cartId, store, replyTo) =>
          client
            .twin()
            .create(
              JsonObject
                .newBuilder
                .set("thingId", s"${dittoConfig.getString("namespace")}:cart-${cartId.value}-${store.value}")
                .set("definition", dittoConfig.getString("thingModel"))
                .set(
                  "attributes",
                  JsonObject
                    .newBuilder
                    .set("id", cartId.value: Long)
                    .set("store", store.value: Long)
                    .set("movable", false)
                    .build
                )
                .build
            )
            .whenComplete(responseHandler(replyTo))
          Behaviors.same[DittoCommand]
        case RemoveCart(cartId, store, replyTo) =>
          client
            .twin()
            .delete(ThingId.of(dittoConfig.getString("namespace"), s"cart-${cartId.value}-${store.value}"))
            .thenCompose(_ =>
              client.policies().delete(PolicyId.of(dittoConfig.getString("namespace"), s"cart-${cartId.value}-${store.value}"))
            )
            .whenComplete(responseHandler(replyTo))
          Behaviors.same[DittoCommand]
        case RaiseCartAlarm(cartId, store) =>
          sendMessage(
            client,
            dittoConfig.getString("namespace"),
            cartId,
            store,
            "raiseAlarm",
            None,
            None
          )
          Behaviors.same[DittoCommand]
        case AssociateCart(cartId, store, customer, replyTo) =>
          sendMessage(
            client,
            dittoConfig.getString("namespace"),
            cartId,
            store,
            "associate",
            Some(JsonObject.of(JsObject("customer" -> customer.toJson).compactPrint)),
            Some(replyTo)
          )
          Behaviors.same[DittoCommand]
        case UnlockCart(cartId, store, replyTo) =>
          sendMessage(
            client,
            dittoConfig.getString("namespace"),
            cartId,
            store,
            "unlock",
            None,
            Some(replyTo)
          )
          Behaviors.same[DittoCommand]
        case LockCart(cartId, store, replyTo) =>
          sendMessage(
            client,
            dittoConfig.getString("namespace"),
            cartId,
            store,
            "lock",
            None,
            Some(replyTo)
          )
          Behaviors.same[DittoCommand]
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
}
