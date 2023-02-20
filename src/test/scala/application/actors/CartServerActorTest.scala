/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import io.github.pervasivecats.Validated
import io.github.pervasivecats.application.actors.MessageBrokerCommand.CartAssociated

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.*
import eu.timepit.refined.auto.autoUnwrap
import org.scalatest.EitherValues.given
import org.scalatest.OptionValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import application.actors.CartServerCommand.*
import application.actors.RootCommand.Startup
import application.routes.entities.CartEntity.StoreCartsShowEntity
import application.routes.entities.Response.*
import carts.cart.entities.*
import carts.cart.valueobjects.*
import carts.cart.Repository.CartNotFound
import carts.RepositoryOperationFailed
import carts.cart.Repository
import carts.cart.domainevents.CartAssociated as CartAssociatedEvent

class CartServerActorTest extends AnyFunSpec with TestContainerForAll {

  private val timeout: FiniteDuration = 300.seconds

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:15.1"),
    databaseName = "carts",
    username = "test",
    password = "test",
    commonJdbcParams = CommonParams(timeout, timeout, Some("carts.sql"))
  )

  private val testKit: ActorTestKit = ActorTestKit()
  private val rootActorProbe: TestProbe[RootCommand] = testKit.createTestProbe[RootCommand]()
  private val messageBrokerActorProbe: TestProbe[MessageBrokerCommand] = testKit.createTestProbe[MessageBrokerCommand]()
  private val cartResponseProbe: TestProbe[CartResponse] = testKit.createTestProbe[CartResponse]()
  private val storeCartsResponseProbe: TestProbe[StoreCartsResponse] = testKit.createTestProbe[StoreCartsResponse]()
  private val emptyResponseProbe: TestProbe[EmptyResponse] = testKit.createTestProbe[EmptyResponse]()

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var cartServer: Option[ActorRef[CartServerCommand]] = None

  private val store: Store = Store(200).getOrElse(fail())
  private val customer: Customer = Customer("addr_3ss!@email.com").getOrElse(fail())

  override def afterContainersStart(containers: Containers): Unit =
    cartServer = Some(
      testKit.spawn(
        CartServerActor(
          rootActorProbe.ref,
          messageBrokerActorProbe.ref,
          ConfigFactory
            .load()
            .getConfig("repository")
            .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
        )
      )
    )

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "scalafix:DisableSyntax.defaultArgs"))
  private def checkLockedCart(cartId: Option[CartId] = None): CartId =
    cartResponseProbe.expectMessageType[CartResponse](10.seconds).result match {
      case Right(c: LockedCart) =>
        c.store shouldBe store
        c.movable shouldBe false
        cartId.foreach(_ shouldBe c.cartId)
        c.cartId
      case _ => fail()
    }

  private def checkUnlockedCart(cartId: CartId): Unit =
    cartResponseProbe.expectMessageType[CartResponse](10.seconds).result match {
      case Right(c: UnlockedCart) =>
        c.cartId shouldBe cartId
        c.store shouldBe store
        c.movable shouldBe true
      case _ => fail()
    }

  private def checkAssociatedCart(cartId: CartId): Unit = {
    cartResponseProbe.expectMessageType[CartResponse](10.seconds).result match {
      case Right(c: AssociatedCart) =>
        c.cartId shouldBe cartId
        c.store shouldBe store
        c.movable shouldBe true
        c.customer shouldBe customer
      case _ => fail()
    }
    messageBrokerActorProbe
      .expectMessage[CartAssociated](10.seconds, CartAssociated(CartAssociatedEvent(cartId, store, customer)))
  }

  private def checkAllLockedCart(cartId: CartId): Unit =
    storeCartsResponseProbe.expectMessageType[StoreCartsResponse](10.seconds).result.value.headOption.value match {
      case Right(c: LockedCart) =>
        c.cartId shouldBe cartId
        c.store shouldBe store
        c.movable shouldBe false
      case _ => fail()
    }

  describe("A cart server actor") {
    describe("when first started up") {
      it("should notify the root actor of its start") {
        rootActorProbe.expectMessage(10.seconds, Startup(true))
      }
    }
  }

  describe("A locked cart") {
    describe("after being added to the database") {
      it("should be present in the database") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(store, cartResponseProbe.ref)
        val cartId: CartId = checkLockedCart()
        server ! ShowAllCarts(store, storeCartsResponseProbe.ref)
        checkAllLockedCart(cartId)
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being added and then deleted from the database") {
      it("should not be present in the database") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(store, cartResponseProbe.ref)
        val cartId: CartId = checkLockedCart()
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
        server ! ShowAllCarts(store, storeCartsResponseProbe.ref)
        storeCartsResponseProbe.expectMessage[StoreCartsResponse](
          10.seconds,
          StoreCartsResponse(Right[ValidationError, Set[Validated[Cart]]](Set.empty))
        )
      }
    }

    describe("if never added to the database") {
      val notExistingCartId: CartId = CartId(9999).getOrElse(fail())
      it("should not be present") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! ShowAllCarts(store, storeCartsResponseProbe.ref)
        storeCartsResponseProbe.expectMessage[StoreCartsResponse](
          10.seconds,
          StoreCartsResponse(Right[ValidationError, Set[Validated[Cart]]](Set.empty))
        )
      }
      it("should not be removable") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! RemoveCart(notExistingCartId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](CartNotFound)))
      }
    }

    describe("after being unlocked") {
      it("should show as unlocked in the database") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(store, cartResponseProbe.ref)
        val cartId: CartId = checkLockedCart()
        server ! UnlockCart(cartId, store, cartResponseProbe.ref)
        checkUnlockedCart(cartId)
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being associated to a customer") {
      it("should show the associated customer in the database") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(store, cartResponseProbe.ref)
        val cartId: CartId = checkLockedCart()
        server ! AssociateCart(cartId, store, customer, cartResponseProbe.ref)
        checkAssociatedCart(cartId)
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being associated then locked again") {
      it("should show as locked in the database") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(store, cartResponseProbe.ref)
        val cartId: CartId = checkLockedCart()
        server ! AssociateCart(cartId, store, customer, cartResponseProbe.ref)
        checkAssociatedCart(cartId)
        server ! LockCart(cartId, store, cartResponseProbe.ref)
        checkLockedCart(Some(cartId))
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }
  }

  describe("A store") {
    describe("when queried to retrieve all carts registered within it, and it has some") {
      it("should return all the requested carts") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(Store(9999).getOrElse(fail()), cartResponseProbe.ref)
        val cartNotInStore: Cart = cartResponseProbe.expectMessageType[CartResponse](10.seconds).result.value
        server ! AddCart(store, cartResponseProbe.ref)
        val firstCartId: CartId = checkLockedCart()
        server ! AddCart(store, cartResponseProbe.ref)
        val secondCartId: CartId = checkLockedCart()
        server ! AddCart(store, cartResponseProbe.ref)
        val thirdCartId: CartId = checkLockedCart()
        server ! ShowAllCarts(store, storeCartsResponseProbe.ref)
        val carts: Set[Validated[Cart]] = storeCartsResponseProbe.expectMessageType[StoreCartsResponse](10.seconds).result.value
        carts should not contain Right[ValidationError, Cart](cartNotInStore)
        carts should have size 3
        carts.map(_.getOrElse(fail())) should contain allOf (
          LockedCart(firstCartId, store),
          LockedCart(secondCartId, store),
          LockedCart(thirdCartId, store)
        )
        server ! RemoveCart(cartNotInStore.cartId, cartNotInStore.store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
        server ! RemoveCart(firstCartId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
        server ! RemoveCart(secondCartId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
        server ! RemoveCart(thirdCartId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("when queried to retrieve all carts registered within it, and it has none") {
      it("should return an empty set") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! ShowAllCarts(store, storeCartsResponseProbe.ref)
        storeCartsResponseProbe.expectMessageType[StoreCartsResponse](10.seconds).result.value shouldBe empty
      }
    }
  }
}
