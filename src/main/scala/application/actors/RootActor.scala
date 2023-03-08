/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.util.concurrent.ForkJoinPool
import javax.sql.DataSource

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.*
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.typesafe.config.Config
import com.zaxxer.hikari.HikariDataSource
import io.getquill.JdbcContextConfig

import application.actors.RootCommand.Startup
import application.routes.Routes

object RootActor {

  def apply(config: Config): Behavior[RootCommand] =
    Behaviors.setup { ctx =>
      val messageBrokerActor: ActorRef[MessageBrokerCommand] = ctx.spawn(
        MessageBrokerActor(ctx.self, config.getConfig("messageBroker")),
        name = "message_broker_actor"
      )
      Behaviors.receiveMessage {
        case Startup(true) =>
          val dataSource: DataSource = JdbcContextConfig(config.getConfig("repository")).dataSource
          val dittoActor: ActorRef[DittoCommand] = ctx.spawn(
            DittoActor(ctx.self, messageBrokerActor, dataSource, config.getConfig("ditto")),
            name = "ditto_actor"
          )
          val serverConfig: Config = config.getConfig("server")
          Behaviors.receiveMessage {
            case Startup(true) =>
              awaitServers(
                ctx.spawn(CartServerActor(ctx.self, messageBrokerActor, dittoActor, dataSource), name = "cart_server"),
                serverConfig,
                count = 0
              )
            case Startup(false) => Behaviors.stopped[RootCommand]
          }
        case Startup(false) => Behaviors.stopped[RootCommand]
      }
    }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def awaitServers(
    cartServer: ActorRef[CartServerCommand],
    serverConfig: Config,
    count: Int
  ): Behavior[RootCommand] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Startup(true) if count < 0 =>
        awaitServers(cartServer, serverConfig, count + 1)
      case Startup(true) =>
        given ActorSystem[_] = ctx.system
        val httpServer: Future[Http.ServerBinding] =
          Http()
            .newServerAt(serverConfig.getString("hostName"), serverConfig.getInt("portNumber"))
            .bind(Routes(cartServer))
        Behaviors.receiveSignal {
          case (_, PostStop) =>
            given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
            httpServer.flatMap(_.unbind()).onComplete(_ => println("Server has stopped"))
            Behaviors.same[RootCommand]
        }
      case Startup(false) => Behaviors.stopped[RootCommand]
    }
  }
}
