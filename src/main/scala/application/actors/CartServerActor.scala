/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.util.concurrent.ForkJoinPool

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.Config

import application.actors.CartServerCommand.*
import application.actors.RootCommand.Startup
import application.routes.entities.Response.*
import application.RequestProcessingFailed
import application.actors.MessageBrokerCommand.CartAssociated
import carts.cart.Repository
import carts.cart.entities.*
import carts.cart.entities.LockedCartOps.unlock
import carts.cart.domainevents.CartAssociated as CartAssociatedEvent

object CartServerActor {

  case object OperationRejected extends ValidationError {

    override val message: String = "The requested operation could not be performed"
  }

  def apply(
    root: ActorRef[RootCommand],
    messageBrokerActor: ActorRef[MessageBrokerCommand],
    repositoryConfig: Config
  ): Behavior[CartServerCommand] =
    Behaviors.setup { ctx =>
      given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
      val repository: Repository = Repository(repositoryConfig)
      root ! Startup(success = true)
      Behaviors.receiveMessage {
        case AssociateCart(cartId, store, customer, replyTo) =>
          Future(for {
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
          } yield a).onComplete {
            case Failure(_) => replyTo ! CartResponse(Left[ValidationError, Cart](RequestProcessingFailed))
            case Success(value) => replyTo ! CartResponse(value)
          }(ctx.executionContext)
          Behaviors.same[CartServerCommand]
        case UnlockCart(cartId, store, replyTo) =>
          Future(for {
            c <- repository.findById(cartId, store)
            u <- c match {
              case cart: LockedCart => Right[ValidationError, UnlockedCart](cart.unlock)
              case _ => Left[ValidationError, UnlockedCart](OperationRejected)
            }
            _ <- repository.update(u)
          } yield u).onComplete {
            case Failure(_) => replyTo ! CartResponse(Left[ValidationError, Cart](RequestProcessingFailed))
            case Success(value) => replyTo ! CartResponse(value)
          }(ctx.executionContext)
          Behaviors.same[CartServerCommand]
        case LockCart(cartId, store, replyTo) =>
          Future(for {
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
          } yield l).onComplete {
            case Failure(_) => replyTo ! CartResponse(Left[ValidationError, Cart](RequestProcessingFailed))
            case Success(value) => replyTo ! CartResponse(value)
          }(ctx.executionContext)
          Behaviors.same[CartServerCommand]
        case AddCart(store, replyTo) =>
          Future(repository.add(store)).onComplete {
            case Failure(_) => replyTo ! CartResponse(Left[ValidationError, Cart](RequestProcessingFailed))
            case Success(value) => replyTo ! CartResponse(value)
          }(ctx.executionContext)
          Behaviors.same[CartServerCommand]
        case RemoveCart(cartId, store, replyTo) =>
          Future(for {
            c <- repository.findById(cartId, store)
            _ <- repository.remove(c)
          } yield ()).onComplete {
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
