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
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import io.github.pervasivecats.application.actors.commands.CartServerCommand
import io.github.pervasivecats.application.actors.commands.CartServerCommand.*
import io.github.pervasivecats.application.actors.commands.DittoCommand
import io.github.pervasivecats.application.actors.commands.DittoCommand.{AddCart => DittoAddCart}
import io.github.pervasivecats.application.actors.commands.DittoCommand.{AssociateCart => DittoAssociateCart}
import io.github.pervasivecats.application.actors.commands.DittoCommand.{LockCart => DittoLockCart}
import io.github.pervasivecats.application.actors.commands.DittoCommand.{RemoveCart => DittoRemoveCart}
import io.github.pervasivecats.application.actors.commands.DittoCommand.{UnlockCart => DittoUnlockCart}
import io.github.pervasivecats.application.actors.commands.MessageBrokerCommand
import io.github.pervasivecats.application.actors.commands.MessageBrokerCommand.CartAssociated
import io.github.pervasivecats.application.actors.commands.RootCommand
import io.github.pervasivecats.application.actors.commands.RootCommand.Startup

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.typesafe.config.Config

import application.routes.entities.Response.*
import application.RequestProcessingFailed
import carts.cart.Repository
import carts.cart.entities.*
import carts.cart.entities.LockedCartOps.unlock
import carts.cart.domainevents.CartAssociated as CartAssociatedEvent

object CartServerActor {

  private given Timeout = 30.seconds

  case object OperationRejected extends ValidationError {

    override val message: String = "The requested operation could not be performed"
  }

  def apply(
    root: ActorRef[RootCommand],
    messageBrokerActor: ActorRef[MessageBrokerCommand],
    dittoActor: ActorRef[DittoCommand],
    dataSource: DataSource
  ): Behavior[CartServerCommand] =
    Behaviors.setup { ctx =>
      given ActorSystem[_] = ctx.system
      given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
      val repository: Repository = Repository(dataSource)
      root ! Startup(success = true)
      Behaviors.receiveMessage {
        case AssociateCart(cartId, store, customer, replyTo) =>
          (dittoActor ? (DittoAssociateCart(cartId, store, customer, _: ActorRef[Validated[Unit]])))
            .map(r =>
              for {
                _ <- r
                c <- repository.findById(cartId, store)
                a <- c match {
                  case _: AssociatedCart => Left[ValidationError, AssociatedCart](OperationRejected)
                  case cart: LockedCart =>
                    import carts.cart.entities.LockedCartOps.associateTo
                    messageBrokerActor ! CartAssociated(CartAssociatedEvent(cartId, store, customer))
                    Right[ValidationError, AssociatedCart](cart.associateTo(customer))
                  case cart: UnlockedCart =>
                    import carts.cart.entities.UnlockedCartOps.associateTo
                    messageBrokerActor ! CartAssociated(CartAssociatedEvent(cartId, store, customer))
                    Right[ValidationError, AssociatedCart](cart.associateTo(customer))
                }
                _ <- repository.update(a)
              } yield a
            )
            .onComplete {
              case Failure(_) => replyTo ! CartResponse(Left[ValidationError, Cart](RequestProcessingFailed))
              case Success(value) => replyTo ! CartResponse(value)
            }(ctx.executionContext)
          Behaviors.same[CartServerCommand]
        case UnlockCart(cartId, store, replyTo) =>
          (dittoActor ? (DittoUnlockCart(cartId, store, _: ActorRef[Validated[Unit]])))
            .map(r =>
              for {
                _ <- r
                c <- repository.findById(cartId, store)
                u <- c match {
                  case cart: LockedCart => Right[ValidationError, UnlockedCart](cart.unlock)
                  case _ => Left[ValidationError, UnlockedCart](OperationRejected)
                }
                _ <- repository.update(u)
              } yield u
            )
            .onComplete {
              case Failure(_) => replyTo ! CartResponse(Left[ValidationError, Cart](RequestProcessingFailed))
              case Success(value) => replyTo ! CartResponse(value)
            }(ctx.executionContext)
          Behaviors.same[CartServerCommand]
        case LockCart(cartId, store, replyTo) =>
          (dittoActor ? (DittoLockCart(cartId, store, _: ActorRef[Validated[Unit]])))
            .map(r =>
              for {
                _ <- r
                c <- repository.findById(cartId, store)
                l <- c match {
                  case cart: AssociatedCart =>
                    import carts.cart.entities.AssociatedCartOps.lock
                    Right[ValidationError, LockedCart](cart.lock)
                  case _: LockedCart => Left[ValidationError, LockedCart](OperationRejected)
                  case cart: UnlockedCart =>
                    import carts.cart.entities.UnlockedCartOps.lock
                    Right[ValidationError, LockedCart](cart.lock)
                }
                _ <- repository.update(l)
              } yield l
            )
            .onComplete {
              case Failure(_) => replyTo ! CartResponse(Left[ValidationError, Cart](RequestProcessingFailed))
              case Success(value) => replyTo ! CartResponse(value)
            }(ctx.executionContext)
          Behaviors.same[CartServerCommand]
        case AddCart(store, replyTo) =>
          Future(repository.add(store))
            .flatMap {
              case Left(error) => Future.successful(Left[ValidationError, Cart](error))
              case Right(cart) =>
                (dittoActor ? (DittoAddCart(cart.cartId, cart.store, _: ActorRef[Validated[Unit]]))).map(_.map(_ => cart))
            }
            .onComplete {
              case Failure(_) => replyTo ! CartResponse(Left[ValidationError, Cart](RequestProcessingFailed))
              case Success(value) => replyTo ! CartResponse(value)
            }(ctx.executionContext)
          Behaviors.same[CartServerCommand]
        case RemoveCart(cartId, store, replyTo) =>
          (dittoActor ? (DittoRemoveCart(cartId, store, _: ActorRef[Validated[Unit]])))
            .map(r =>
              for {
                _ <- r
                c <- repository.findById(cartId, store)
                _ <- repository.remove(c)
              } yield ()
            )
            .onComplete {
              case Failure(_) => replyTo ! EmptyResponse(Left[ValidationError, Unit](RequestProcessingFailed))
              case Success(value) => replyTo ! EmptyResponse(value)
            }(ctx.executionContext)
          Behaviors.same[CartServerCommand]
        case ShowAllCarts(store, replyTo) =>
          Future(repository.findByStore(store)).onComplete {
            case Failure(_) => replyTo ! StoreCartsResponse(Left[ValidationError, Set[Validated[Cart]]](RequestProcessingFailed))
            case Success(value) => replyTo ! StoreCartsResponse(value)
          }
          Behaviors.same[CartServerCommand]
      }
    }
}
