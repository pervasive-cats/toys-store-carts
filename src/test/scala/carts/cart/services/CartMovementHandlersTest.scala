/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.services

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.getquill.JdbcContextConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import application.actors.DittoCommand
import application.actors.DittoCommand.RaiseCartAlarm
import carts.cart.{entities, Repository}
import carts.cart.domainevents.CartMoved
import carts.cart.entities.*
import carts.cart.entities.LockedCartOps.{associateTo, unlock}
import carts.cart.valueobjects.*

class CartMovementHandlersTest extends AnyFunSpec with TestContainerForAll with BeforeAndAfterAll {

  private val timeout: FiniteDuration = 300.seconds

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:15.1"),
    databaseName = "carts",
    username = "test",
    password = "test",
    commonJdbcParams = CommonParams(timeout, timeout, Some("carts.sql"))
  )

  private val testKit: ActorTestKit = ActorTestKit()
  private val dittoActorProbe: TestProbe[DittoCommand] = testKit.createTestProbe()
  private val cartMovementHandlers: CartMovementHandlers = CartMovementHandlers(dittoActorProbe.ref)

  override def afterAll(): Unit = testKit.shutdownTestKit()

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var repository: Option[Repository] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var maybeLockedCart: Option[LockedCart] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var maybeUnlockedCart: Option[UnlockedCart] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var maybeAssociatedCart: Option[AssociatedCart] = None

  override def afterContainersStart(containers: Containers): Unit = {
    val db: Repository = Repository(
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
    repository = Some(db)
    val store: Store = Store(1).getOrElse(fail())
    maybeLockedCart = Some(db.add(store).getOrElse(fail()))
    val unlockedCart: UnlockedCart = db.add(store).getOrElse(fail()).unlock
    db.update(unlockedCart).getOrElse(fail())
    maybeUnlockedCart = Some(unlockedCart)
    val customer: Customer = Customer("mar10@mail.com").getOrElse(fail())
    val associatedCart: AssociatedCart = db.add(store).getOrElse(fail()).associateTo(customer)
    db.update(associatedCart).getOrElse(fail())
    maybeAssociatedCart = Some(associatedCart)
  }

  describe("A onCartMoved handler") {
    given Repository = repository.getOrElse(fail())

    describe("when invoked with a locked cart") {
      it("should send a message to Ditto raising the cart alarm") {
        val lockedCart: LockedCart = maybeLockedCart.getOrElse(fail())
        cartMovementHandlers.onCartMoved(CartMoved(lockedCart.cartId, lockedCart.store))
        dittoActorProbe.expectMessage(10.seconds, RaiseCartAlarm(lockedCart.cartId, lockedCart.store))
      }
    }

    describe("when invoked with an unlocked cart") {
      it("should do nothing") {
        val unlockedCart: UnlockedCart = maybeUnlockedCart.getOrElse(fail())
        cartMovementHandlers.onCartMoved(CartMoved(unlockedCart.cartId, unlockedCart.store))
        dittoActorProbe.expectNoMessage(10.seconds)
      }
    }

    describe("when invoked with an associated cart") {
      it("should do nothing") {
        val associatedCart: AssociatedCart = maybeAssociatedCart.getOrElse(fail())
        cartMovementHandlers.onCartMoved(CartMoved(associatedCart.cartId, associatedCart.store))
        dittoActorProbe.expectNoMessage(10.seconds)
      }
    }
  }
}
