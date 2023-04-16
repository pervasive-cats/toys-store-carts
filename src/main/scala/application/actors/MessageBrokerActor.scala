/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.nio.charset.StandardCharsets
import java.util.UUID

import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.Try

import io.github.pervasivecats.application.actors.commands.MessageBrokerCommand
import io.github.pervasivecats.application.actors.commands.MessageBrokerCommand.*
import io.github.pervasivecats.application.actors.commands.RootCommand
import io.github.pervasivecats.application.actors.commands.RootCommand.Startup

import akka.actor.typed.*
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import com.rabbitmq.client.*
import com.typesafe.config.Config
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsonFormat
import spray.json.enrichAny
import spray.json.enrichString

import application.routes.entities.Entity
import carts.cart.domainevents.{
  CartAssociated as CartAssociatedEvent,
  CartMoved as CartMovedEvent,
  ItemAddedToCart as ItemAddedToCartEvent,
  ItemInsertedIntoCart as ItemInsertedIntoCartEvent
}
import AnyOps.===
import application.routes.entities.Entity.{ResultResponseEntity, given}
import application.Serializers.given
import carts.cart.Repository

object MessageBrokerActor {

  private def publish[A <: Entity: JsonFormat](channel: Channel, response: A, routingKey: String, correlationId: String): Unit =
    channel.basicPublish(
      "carts",
      routingKey,
      AMQP
        .BasicProperties
        .Builder()
        .contentType("application/json")
        .deliveryMode(2)
        .priority(0)
        .replyTo("carts")
        .correlationId(correlationId)
        .build(),
      response.toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
    )

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def apply(root: ActorRef[RootCommand], messageBrokerConfig: Config): Behavior[MessageBrokerCommand] =
    Behaviors.setup { ctx =>
      Try {
        val factory: ConnectionFactory = ConnectionFactory()
        factory.setUsername(messageBrokerConfig.getString("username"))
        factory.setPassword(messageBrokerConfig.getString("password"))
        factory.setVirtualHost(messageBrokerConfig.getString("virtualHost"))
        factory.setHost(messageBrokerConfig.getString("hostName"))
        factory.setPort(messageBrokerConfig.getInt("portNumber"))
        factory.newConnection()
      }.flatMap { c =>
        val channel: Channel = c.createChannel()
        channel.addReturnListener((r: Return) => {
          ctx.system.deadLetters[String] ! String(r.getBody, StandardCharsets.UTF_8)
          channel.basicPublish(
            "dead_letters",
            "dead_letters",
            AMQP
              .BasicProperties
              .Builder()
              .contentType("application/json")
              .deliveryMode(2)
              .priority(0)
              .build(),
            r.getBody
          )
        })
        Try {
          channel.exchangeDeclare("dead_letters", BuiltinExchangeType.FANOUT, true)
          channel.queueDeclare("dead_letters", true, false, false, Map.empty.asJava)
          channel.queueBind("dead_letters", "dead_letters", "")
          val couples: Seq[(String, String)] = Seq(
            "carts" -> "items",
            "carts" -> "shopping"
          )
          val queueArgs: Map[String, String] = Map("x-dead-letter-exchange" -> "dead_letters")
          couples.flatMap(Seq(_, _)).distinct.foreach(e => channel.exchangeDeclare(e, BuiltinExchangeType.TOPIC, true))
          couples
            .flatMap((b1, b2) => Seq(b1 + "_" + b2, b2 + "_" + b1))
            .foreach(q => channel.queueDeclare(q, true, false, false, queueArgs.asJava))
          couples
            .flatMap((b1, b2) => Seq((b1, b1 + "_" + b2, b2), (b2, b2 + "_" + b1, b1)))
            .foreach((e, q, r) => channel.queueBind(q, e, r))
          (c, channel)
        }
      }.map { (co, ch) =>
        root ! Startup(true)
        Behaviors
          .receiveMessage[MessageBrokerCommand] {
            case ItemAddedToCart(event) =>
              publish(ch, ResultResponseEntity(event), "items", UUID.randomUUID().toString)
              publish(ch, ResultResponseEntity(event), "shopping", UUID.randomUUID().toString)
              Behaviors.same[MessageBrokerCommand]
            case CartAssociated(event) =>
              publish(ch, ResultResponseEntity(event), "shopping", UUID.randomUUID().toString)
              Behaviors.same[MessageBrokerCommand]
          }
          .receiveSignal {
            case (_, PostStop) =>
              ch.close()
              co.close()
              Behaviors.same[MessageBrokerCommand]
          }
      }.getOrElse {
        root ! Startup(false)
        Behaviors.stopped[MessageBrokerCommand]
      }
    }
}
