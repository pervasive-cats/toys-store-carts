/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart

import java.nio.file.attribute.UserPrincipalNotFoundException

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import carts.cart.entities.{AssociatedCart, Cart, LockedCart, UnlockedCart}
import carts.cart.valueobjects.{CartId, Customer, Store}
import carts.cart.Repository.{CartAlreadyPresent, CartNotFound, OperationFailed}

class RepositoryTest extends AnyFunSpec with TestContainerForAll {

  private val timeout: FiniteDuration = 300.seconds

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:15.1"),
    databaseName = "carts",
    username = "test",
    password = "test",
    commonJdbcParams = CommonParams(timeout, timeout, Some("carts.sql"))
  )

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var repository: Option[Repository] = None

  override def afterContainersStart(containers: Containers): Unit =
    repository = Some(
      Repository(
        ConfigFactory
          .load()
          .getConfig("ctx")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
      )
    )

  private val cartId: CartId = CartId(15).getOrElse(fail())
  private val store: Store = Store(200).getOrElse(fail())
  private val customer: Customer = Customer("addr_3ss!@email.com").getOrElse(fail())

  describe("A locked cart") {
    describe("after being added to the database") {
      it("should be present in the database") {
        val db: Repository = repository.getOrElse(fail())
        db.add(LockedCart(cartId, store))
        val cart: Cart = db.findById(cartId, store).getOrElse(fail())
        cart.cartId shouldBe cartId
        cart.store shouldBe store
        cart.isMovable shouldBe false
        db.remove(cart).getOrElse(fail())
      }
    }

    describe("after being added and then deleted from the database") {
      it("should not be present in the database") {
        val db: Repository = repository.getOrElse(fail())
        val lockedCart: Cart = LockedCart(cartId, store)
        db.add(lockedCart).getOrElse(fail())
        db.remove(lockedCart).getOrElse(fail())
        db.findById(cartId, store).left.value shouldBe CartNotFound
      }
    }

    describe("if never added to the database") {
      it("should not be present") {
        val db: Repository = repository.getOrElse(fail())
        db.findById(cartId, store).left.value shouldBe CartNotFound
        db.remove(LockedCart(cartId, store)).left.value shouldBe OperationFailed
      }
    }

    describe("if already registered") {
      it("should not allow a new registration") {
        val db: Repository = repository.getOrElse(fail())
        val lockedCart: Cart = LockedCart(cartId, store)
        db.add(lockedCart).getOrElse(fail())
        db.add(lockedCart).left.value shouldBe CartAlreadyPresent
        db.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("after being unlocked") {
      it("should show as unlocked in the database") {
        val db: Repository = repository.getOrElse(fail())
        db.add(LockedCart(cartId, store)).getOrElse(fail())
        db.findById(cartId, store).getOrElse(fail()).isMovable shouldBe false
        db.update(UnlockedCart(cartId, store)).getOrElse(fail())
        db.findById(cartId, store).getOrElse(fail()).isMovable shouldBe true
        db.remove(UnlockedCart(cartId, store)).getOrElse(fail())
      }
    }

    describe("after being associated to a customer") {
      it("should show the associated customer in the database") {
        val db: Repository = repository.getOrElse(fail())
        db.add(LockedCart(cartId, store)).getOrElse(fail())
        db.findById(cartId, store).getOrElse(fail()).isMovable shouldBe false
        db.update(AssociatedCart(cartId, store, customer)).getOrElse(fail())
        val cart: Cart = db.findById(cartId, store).getOrElse(fail())
        cart match {
          case cart: AssociatedCart =>
            cart.isMovable shouldBe true
            cart.customer shouldBe customer
          case _ => fail()
        }
        db.remove(AssociatedCart(cartId, store, customer)).getOrElse(fail())
      }
    }

    describe("after being associated then locked again") {
      it("should show as locked in the database") {
        val db: Repository = repository.getOrElse(fail())
        db.add(LockedCart(cartId, store)).getOrElse(fail())
        db.update(AssociatedCart(cartId, store, customer)).getOrElse(fail())
        db.update(LockedCart(cartId, store)).getOrElse(fail())
        db.findById(cartId, store).getOrElse(fail()).isMovable shouldBe false
        db.remove(LockedCart(cartId, store)).getOrElse(fail())
      }
    }
  }

}
