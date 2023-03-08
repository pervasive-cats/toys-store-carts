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

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.*
import eu.timepit.refined.auto.autoUnwrap
import io.getquill.JdbcContextConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.EitherValues.given
import org.scalatest.OptionValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import application.actors.MessageBrokerCommand.CartAssociated
import application.actors.DittoCommand.{
  AddCart as DittoAddCart,
  AssociateCart as DittoAssociateCart,
  LockCart as DittoLockCart,
  RemoveCart as DittoRemoveCart,
  UnlockCart as DittoUnlockCart
}
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

class CartServerActorTest extends AnyFunSpec with TestContainerForAll with BeforeAndAfterAll {

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
  private val dittoActorProbe: TestProbe[DittoCommand] = testKit.createTestProbe[DittoCommand]()
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
          dittoActorProbe.ref,
          JdbcContextConfig(
            ConfigFactory
              .load()
              .getConfig("repository")
              .withValue(
                "dataSource.portNumber",
                ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue())
              )
          ).dataSource
        )
      )
    )

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private def checkAddition(store: Store): CartId =
    val dittoMessage: DittoAddCart = dittoActorProbe.expectMessageType[DittoAddCart](10.seconds)
    dittoMessage.store shouldBe store
    dittoMessage.replyTo ! Right[ValidationError, Unit](())
    cartResponseProbe.expectMessageType[CartResponse](10.seconds).result match {
      case Right(c: LockedCart) =>
        c.store shouldBe store
        c.movable shouldBe false
        c.cartId shouldBe dittoMessage.cartId
        c.cartId
      case _ => fail()
    }

  private def checkAssociatedCart(cartId: CartId): Unit = {
    val dittoMessage: DittoAssociateCart = dittoActorProbe.expectMessageType[DittoAssociateCart](10.seconds)
    dittoMessage.cartId shouldBe cartId
    dittoMessage.store shouldBe store
    dittoMessage.customer shouldBe customer
    dittoMessage.replyTo ! Right[ValidationError, Unit](())
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

  private def checkDeletion(cartId: CartId, store: Store): Unit = {
    val dittoMessage: DittoRemoveCart = dittoActorProbe.expectMessageType[DittoRemoveCart](10.seconds)
    dittoMessage.cartId shouldBe cartId
    dittoMessage.store shouldBe store
    dittoMessage.replyTo ! Right[ValidationError, Unit](())
    emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
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
        val cartId: CartId = checkAddition(store)
        server ! ShowAllCarts(store, storeCartsResponseProbe.ref)
        storeCartsResponseProbe.expectMessageType[StoreCartsResponse](10.seconds).result.value.headOption.value match {
          case Right(c: LockedCart) =>
            c.cartId shouldBe cartId
            c.store shouldBe store
            c.movable shouldBe false
          case _ => fail()
        }
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        checkDeletion(cartId, store)
      }
    }

    describe("after being added and then deleted from the database") {
      it("should not be present in the database") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(store, cartResponseProbe.ref)
        val cartId: CartId = checkAddition(store)
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        checkDeletion(cartId, store)
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
        val dittoMessage: DittoRemoveCart = dittoActorProbe.expectMessageType[DittoRemoveCart](10.seconds)
        dittoMessage.cartId shouldBe notExistingCartId
        dittoMessage.store shouldBe store
        dittoMessage.replyTo ! Left[ValidationError, Unit](CartNotFound)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](CartNotFound)))
      }
    }

    describe("after being unlocked") {
      it("should show as unlocked in the database") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(store, cartResponseProbe.ref)
        val cartId: CartId = checkAddition(store)
        server ! UnlockCart(cartId, store, cartResponseProbe.ref)
        val dittoMessage: DittoUnlockCart = dittoActorProbe.expectMessageType[DittoUnlockCart](10.seconds)
        dittoMessage.cartId shouldBe cartId
        dittoMessage.store shouldBe store
        dittoMessage.replyTo ! Right[ValidationError, Unit](())
        cartResponseProbe.expectMessageType[CartResponse](10.seconds).result match {
          case Right(c: UnlockedCart) =>
            c.cartId shouldBe cartId
            c.store shouldBe store
            c.movable shouldBe true
          case _ => fail()
        }
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        checkDeletion(cartId, store)
      }
    }

    describe("after being associated to a customer") {
      it("should show the associated customer in the database") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(store, cartResponseProbe.ref)
        val cartId: CartId = checkAddition(store)
        server ! AssociateCart(cartId, store, customer, cartResponseProbe.ref)
        checkAssociatedCart(cartId)
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        checkDeletion(cartId, store)
      }
    }

    describe("after being associated then locked again") {
      it("should show as locked in the database") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        server ! AddCart(store, cartResponseProbe.ref)
        val cartId: CartId = checkAddition(store)
        server ! AssociateCart(cartId, store, customer, cartResponseProbe.ref)
        checkAssociatedCart(cartId)
        server ! LockCart(cartId, store, cartResponseProbe.ref)
        val dittoMessage: DittoLockCart = dittoActorProbe.expectMessageType[DittoLockCart](10.seconds)
        dittoMessage.cartId shouldBe cartId
        dittoMessage.store shouldBe store
        dittoMessage.replyTo ! Right[ValidationError, Unit](())
        cartResponseProbe.expectMessageType[CartResponse](10.seconds).result match {
          case Right(c: LockedCart) =>
            c.cartId shouldBe cartId
            c.store shouldBe store
            c.movable shouldBe false
          case _ => fail()
        }
        server ! RemoveCart(cartId, store, emptyResponseProbe.ref)
        checkDeletion(cartId, store)
      }
    }
  }

  describe("A store") {
    describe("when queried to retrieve all carts registered within it, and it has some") {
      it("should return all the requested carts") {
        val server: ActorRef[CartServerCommand] = cartServer.getOrElse(fail())
        val extraStore: Store = Store(9999).getOrElse(fail())
        server ! AddCart(extraStore, cartResponseProbe.ref)
        val cartNotInStoreId: CartId = checkAddition(extraStore)
        server ! AddCart(store, cartResponseProbe.ref)
        val firstCartId: CartId = checkAddition(store)
        server ! AddCart(store, cartResponseProbe.ref)
        val secondCartId: CartId = checkAddition(store)
        server ! AddCart(store, cartResponseProbe.ref)
        val thirdCartId: CartId = checkAddition(store)
        server ! ShowAllCarts(store, storeCartsResponseProbe.ref)
        val carts: Set[Validated[Cart]] = storeCartsResponseProbe.expectMessageType[StoreCartsResponse](10.seconds).result.value
        carts should not contain Right[ValidationError, Cart](LockedCart(cartNotInStoreId, extraStore))
        carts should have size 3
        carts.map(_.getOrElse(fail())) should contain allOf (
          LockedCart(firstCartId, store),
          LockedCart(secondCartId, store),
          LockedCart(thirdCartId, store)
        )
        server ! RemoveCart(cartNotInStoreId, extraStore, emptyResponseProbe.ref)
        checkDeletion(cartNotInStoreId, extraStore)
        server ! RemoveCart(firstCartId, store, emptyResponseProbe.ref)
        checkDeletion(firstCartId, store)
        server ! RemoveCart(secondCartId, store, emptyResponseProbe.ref)
        checkDeletion(secondCartId, store)
        server ! RemoveCart(thirdCartId, store, emptyResponseProbe.ref)
        checkDeletion(thirdCartId, store)
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
