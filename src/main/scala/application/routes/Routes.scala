/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import io.github.pervasivecats.application.actors.commands.CartServerCommand
import io.github.pervasivecats.application.actors.commands.CartServerCommand.*

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.*
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.util.Timeout
import spray.json.JsonWriter

import application.actors.CartServerActor.OperationRejected
import application.routes.entities.CartEntity.*
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity, given}
import application.routes.entities.Response
import application.routes.entities.Response.{CartResponse, EmptyResponse, StoreCartsResponse}
import application.Serializers.given
import carts.cart.entities.Cart
import carts.cart.Repository
import carts.cart.Repository.CartNotFound

object Routes extends Directives with SprayJsonSupport {

  case object RequestFailed extends ValidationError {

    override val message: String = "An error has occurred while processing the request"
  }

  case class DeserializationFailed(message: String) extends ValidationError

  private val rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(msg, _) =>
          complete(StatusCodes.BadRequest, ErrorResponseEntity(DeserializationFailed(msg)))
      }
      .result()

  private given timeout: Timeout = 30.seconds

  private def route[A: FromRequestUnmarshaller, B, C <: Response[D], D: JsonWriter](
    server: ActorRef[B],
    request: A => ActorRef[C] => B,
    responseHandler: C => Route
  )(
    using
    ActorSystem[_]
  ): Route =
    entity(as[A]) { e =>
      onComplete(server ? request(e)) {
        case Failure(_) => complete(StatusCodes.InternalServerError, ErrorResponseEntity(RequestFailed))
        case Success(value) => responseHandler(value)
      }
    }

  def apply(cartServer: ActorRef[CartServerCommand])(using ActorSystem[_]): Route = handleRejections(rejectionHandler) {
    concat(
      path("cart") {
        concat(
          post {
            route[CartAdditionEntity, AddCart, CartResponse, Cart](
              cartServer,
              e => AddCart(e.store, _),
              _.result match {
                case Left(error) => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
                case Right(value) => complete(ResultResponseEntity(value))
              }
            )
          },
          delete {
            route[CartRemovalEntity, RemoveCart, EmptyResponse, Unit](
              cartServer,
              e => RemoveCart(e.id, e.store, _),
              _.result match {
                case Left(error) =>
                  error match {
                    case CartNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(error))
                    case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
                  }
                case Right(value) => complete(ResultResponseEntity(value))
              }
            )
          }
        )
      },
      path("cart" / "associate") {
        put {
          route[CartAssociationEntity, AssociateCart, CartResponse, Cart](
            cartServer,
            e => AssociateCart(e.cartId, e.store, e.customer, _),
            _.result match {
              case Left(error) =>
                error match {
                  case CartNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(error))
                  case OperationRejected => complete(StatusCodes.BadRequest, ErrorResponseEntity(error))
                  case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
                }
              case Right(value) => complete(ResultResponseEntity(value))
            }
          )
        }
      },
      path("cart" / "lock") {
        put {
          route[CartLockEntity, LockCart, CartResponse, Cart](
            cartServer,
            e => LockCart(e.cartId, e.store, _),
            _.result match {
              case Left(error) =>
                error match {
                  case CartNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(error))
                  case OperationRejected => complete(StatusCodes.BadRequest, ErrorResponseEntity(error))
                  case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
                }
              case Right(value) => complete(ResultResponseEntity(value))
            }
          )
        }
      },
      path("cart" / "unlock") {
        put {
          route[CartUnlockEntity, UnlockCart, CartResponse, Cart](
            cartServer,
            e => UnlockCart(e.cartId, e.store, _),
            _.result match {
              case Left(error) =>
                error match {
                  case CartNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(error))
                  case OperationRejected => complete(StatusCodes.BadRequest, ErrorResponseEntity(error))
                  case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
                }
              case Right(value) => complete(ResultResponseEntity(value))
            }
          )
        }
      },
      path("cart" / "store") {
        get {
          route[StoreCartsShowEntity, ShowAllCarts, StoreCartsResponse, Set[Validated[Cart]]](
            cartServer,
            e => ShowAllCarts(e.store, _),
            _.result match {
              case Left(error) => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
              case Right(value) => complete(ResultResponseEntity(value))
            }
          )
        }
      }
    )
  }
}
