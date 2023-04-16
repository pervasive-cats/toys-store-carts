/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes

import scala.concurrent.duration.DurationInt

import io.github.pervasivecats.application.actors.commands.CartServerCommand
import io.github.pervasivecats.application.actors.commands.CartServerCommand.*

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.*
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.EitherValues.*
import org.scalatest.OptionValues.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import spray.json.enrichAny

import application.routes.entities.CartEntity.*
import application.routes.entities.Entity.*
import application.routes.entities.Entity.given
import application.Serializers.given
import application.routes.entities.Response.*
import application.routes.Routes.DeserializationFailed
import carts.cart.entities.*
import carts.cart.valueobjects.*
import carts.RepositoryOperationFailed
import carts.cart.Repository.CartNotFound

class RoutesTest extends AnyFunSpec with ScalatestRouteTest with SprayJsonSupport {

  private given typedSystem: ActorSystem[_] = system.toTyped

  private val cartServerProbe = TestProbe[CartServerCommand]()

  private val routes: Route = Routes(cartServerProbe.ref)
  private val cartId: CartId = CartId(1).getOrElse(fail())
  private val store: Store = Store(1).getOrElse(fail())
  private val customer: Customer = Customer("mar10@mail.com").getOrElse(fail())
  private val lockedCart: LockedCart = LockedCart(cartId, store)
  private val unlockedCart: UnlockedCart = UnlockedCart(cartId, store)
  private val associatedCart: AssociatedCart = AssociatedCart(cartId, store, customer)

  private def checkLockedCart(actualCart: Cart): Unit =
    actualCart match {
      case c: LockedCart =>
        c.cartId shouldBe cartId
        c.store shouldBe store
      case _ => fail()
    }

  private def checkUnlockedCart(actualCart: Cart): Unit =
    actualCart match {
      case c: UnlockedCart =>
        c.cartId shouldBe cartId
        c.store shouldBe store
      case _ => fail()
    }

  private def checkAssociatedCart(actualCart: Cart): Unit =
    actualCart match {
      case c: AssociatedCart =>
        c.cartId shouldBe cartId
        c.store shouldBe store
        c.customer shouldBe customer
      case _ => fail()
    }

  describe("A cart service") {
    describe("when sending a GET request to the /cart/store endpoint") {
      it("should send a response returning all carts for a given store if everything is correct") {
        val test: RouteTestResult = Get("/cart/store", StoreCartsShowEntity(store)) ~> routes
        val message: CartServerCommand = cartServerProbe.receiveMessage(10.seconds)
        message match {
          case ShowAllCarts(s, r) =>
            s shouldBe store
            r ! StoreCartsResponse(Right[ValidationError, Set[Validated[Cart]]](Set(Right[ValidationError, Cart](lockedCart))))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          val result: Set[Validated[Cart]] = entityAs[ResultResponseEntity[Set[Validated[Cart]]]].result
          result should have size 1
          checkLockedCart(result.headOption.value.value)
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult = Get("/cart/store", StoreCartsShowEntity(store)) ~> routes
        val message: ShowAllCarts = cartServerProbe.expectMessageType[ShowAllCarts](10.seconds)
        message.replyTo ! StoreCartsResponse(
          Left[ValidationError, Set[Validated[Cart]]](RepositoryOperationFailed)
        )
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }
    }

    describe("when sending a POST request to the /cart endpoint") {
      it("should send a response creating a new cart if everything is correct") {
        val test: RouteTestResult = Post("/cart", CartAdditionEntity(store)) ~> routes
        val message: CartServerCommand = cartServerProbe.receiveMessage(10.seconds)
        message match {
          case AddCart(s, r) =>
            s shouldBe store
            r ! CartResponse(Right[ValidationError, Cart](lockedCart))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkLockedCart(entityAs[ResultResponseEntity[Cart]].result)
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult = Post("/cart", CartAdditionEntity(store)) ~> routes
        val message: AddCart = cartServerProbe.expectMessageType[AddCart](10.seconds)
        message.replyTo ! CartResponse(Left[ValidationError, Cart](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Post("/cart", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'store'")
        }
      }
    }

    describe("when sending a DELETE request to the /cart endpoint") {
      it("should send a response removing a cart if everything is correct") {
        val test: RouteTestResult = Delete("/cart", CartRemovalEntity(cartId, store)) ~> routes
        val message: CartServerCommand = cartServerProbe.receiveMessage(10.seconds)
        message match {
          case RemoveCart(i, s, r) =>
            i shouldBe cartId
            s shouldBe store
            r ! EmptyResponse(Right[ValidationError, Unit](()))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ResultResponseEntity[Unit]].result shouldBe ()
        }
      }

      it("should send a 404 response if the cart does not exists") {
        val test: RouteTestResult = Delete("/cart", CartRemovalEntity(cartId, store)) ~> routes
        val message: RemoveCart = cartServerProbe.expectMessageType[RemoveCart](10.seconds)
        message.replyTo ! EmptyResponse(Left[ValidationError, Unit](CartNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe CartNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult = Delete("/cart", CartRemovalEntity(cartId, store)) ~> routes
        val message: RemoveCart = cartServerProbe.expectMessageType[RemoveCart](10.seconds)
        message.replyTo ! EmptyResponse(Left[ValidationError, Unit](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Delete("/cart", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }

    describe("when sending a PUT request to the /cart/associated endpoint") {
      it("should send a response updating the cart state to associated if everything is correct") {
        val firstTest: RouteTestResult = Put("/cart/associate", CartAssociationEntity(cartId, store, customer)) ~> routes
        val firstMessage: CartServerCommand = cartServerProbe.receiveMessage(10.seconds)
        firstMessage match {
          case AssociateCart(i, s, c, r) =>
            i shouldBe cartId
            s shouldBe store
            c shouldBe customer
            r ! CartResponse(Right[ValidationError, Cart](associatedCart))
          case _ => fail()
        }
        firstTest ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkAssociatedCart(entityAs[ResultResponseEntity[Cart]].result)
        }
        val secondTest: RouteTestResult = Put("/cart/associate", CartAssociationEntity(cartId, store, customer)) ~> routes
        val secondMessage: CartServerCommand = cartServerProbe.receiveMessage(10.seconds)
        secondMessage match {
          case AssociateCart(i, s, c, r) =>
            i shouldBe cartId
            s shouldBe store
            c shouldBe customer
            r ! CartResponse(Right[ValidationError, Cart](associatedCart))
          case _ => fail()
        }
        secondTest ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkAssociatedCart(entityAs[ResultResponseEntity[Cart]].result)
        }
      }

      it("should send a 404 response if the cart does not exists") {
        val test: RouteTestResult = Put("/cart/associate", CartAssociationEntity(cartId, store, customer)) ~> routes
        val message: AssociateCart = cartServerProbe.expectMessageType[AssociateCart](10.seconds)
        message.replyTo ! CartResponse(Left[ValidationError, Cart](CartNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe CartNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult = Put("/cart/associate", CartAssociationEntity(cartId, store, customer)) ~> routes
        val message: AssociateCart = cartServerProbe.expectMessageType[AssociateCart](10.seconds)
        message.replyTo ! CartResponse(Left[ValidationError, Cart](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Put("/cart/unlock", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'cartId'")
        }
      }
    }

    describe("when sending a PUT request to the /cart/unlock endpoint") {
      it("should send a response updating the cart state to unlocked if everything is correct") {
        val test: RouteTestResult = Put("/cart/unlock", CartUnlockEntity(cartId, store)) ~> routes
        val message: CartServerCommand = cartServerProbe.receiveMessage(10.seconds)
        message match {
          case UnlockCart(i, s, r) =>
            i shouldBe cartId
            s shouldBe store
            r ! CartResponse(Right[ValidationError, Cart](unlockedCart))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkUnlockedCart(entityAs[ResultResponseEntity[Cart]].result)
        }
      }

      it("should send a 404 response if the cart does not exists") {
        val test: RouteTestResult = Put("/cart/unlock", CartUnlockEntity(cartId, store)) ~> routes
        val message: UnlockCart = cartServerProbe.expectMessageType[UnlockCart](10.seconds)
        message.replyTo ! CartResponse(Left[ValidationError, Cart](CartNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe CartNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult = Put("/cart/unlock", CartUnlockEntity(cartId, store)) ~> routes
        val message: UnlockCart = cartServerProbe.expectMessageType[UnlockCart](10.seconds)
        message.replyTo ! CartResponse(Left[ValidationError, Cart](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Put("/cart/unlock", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'cartId'")
        }
      }
    }

    describe("when sending a PUT request to the /cart/lock endpoint") {
      it("should send a response updating the cart state to unlocked if everything is correct") {
        val firstTest: RouteTestResult = Put("/cart/lock", CartLockEntity(cartId, store)) ~> routes
        val firstMessage: CartServerCommand = cartServerProbe.receiveMessage(10.seconds)
        firstMessage match {
          case LockCart(i, s, r) =>
            i shouldBe cartId
            s shouldBe store
            r ! CartResponse(Right[ValidationError, Cart](lockedCart))
          case _ => fail()
        }
        firstTest ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkLockedCart(entityAs[ResultResponseEntity[Cart]].result)
        }
        val secondTest: RouteTestResult = Put("/cart/lock", CartLockEntity(cartId, store)) ~> routes
        val secondMessage: CartServerCommand = cartServerProbe.receiveMessage(10.seconds)
        secondMessage match {
          case LockCart(i, s, r) =>
            i shouldBe cartId
            s shouldBe store
            r ! CartResponse(Right[ValidationError, Cart](lockedCart))
          case _ => fail()
        }
        secondTest ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkLockedCart(entityAs[ResultResponseEntity[Cart]].result)
        }
      }

      it("should send a 404 response if the cart does not exists") {
        val test: RouteTestResult = Put("/cart/lock", CartLockEntity(cartId, store)) ~> routes
        val message: LockCart = cartServerProbe.expectMessageType[LockCart](10.seconds)
        message.replyTo ! CartResponse(Left[ValidationError, Cart](CartNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe CartNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult = Put("/cart/lock", CartLockEntity(cartId, store)) ~> routes
        val message: LockCart = cartServerProbe.expectMessageType[LockCart](10.seconds)
        message.replyTo ! CartResponse(Left[ValidationError, Cart](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Put("/cart/unlock", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'cartId'")
        }
      }
    }
  }
}
