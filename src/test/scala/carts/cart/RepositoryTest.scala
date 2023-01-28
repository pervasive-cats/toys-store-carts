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

  private val store: Store = Store(200).getOrElse(fail())
  private val customer: Customer = Customer("addr_3ss!@email.com").getOrElse(fail())

  describe("A locked cart") {
    describe("after being added to the database") {
      it("should be present in the database") {
        val db: Repository = repository.getOrElse(fail())
        val cart: LockedCart = db.add(store).getOrElse(fail())
        val foundCart: Cart = db.findById(cart.cartId).getOrElse(fail())
        foundCart.cartId.value shouldBe cart.cartId.value
        foundCart.store.value shouldBe cart.store.value
        foundCart.store shouldBe store
        foundCart.movable shouldBe false
        db.remove(cart).getOrElse(fail())
      }
    }

    describe("after being added and then deleted from the database") {
      it("should not be present in the database") {
        val db: Repository = repository.getOrElse(fail())
        val lockedCart: Cart = db.add(store).getOrElse(fail())
        db.remove(lockedCart).getOrElse(fail())
        db.findById(lockedCart.cartId).left.value shouldBe CartNotFound
      }
    }

    describe("if never added to the database") {
      val notExists = LockedCart(CartId(9999).getOrElse(fail()), store)
      it("should not be present") {
        val db: Repository = repository.getOrElse(fail())
        db.findById(notExists.cartId).left.value shouldBe CartNotFound
      }
      it("should not be updatable or removable") {
        val db: Repository = repository.getOrElse(fail())
        db.update(notExists).left.value shouldBe OperationFailed
      }
      it("should not be removable") {
        val db: Repository = repository.getOrElse(fail())
        db.remove(notExists).left.value shouldBe OperationFailed
      }
    }

    describe("after being unlocked") {
      it("should show as unlocked in the database") {
        val db: Repository = repository.getOrElse(fail())
        val cart: Cart = db.add(store).getOrElse(fail())
        db.findById(cart.cartId).getOrElse(fail()).movable shouldBe false
        db.update(UnlockedCart(cart.cartId, store)).getOrElse(fail())
        db.findById(cart.cartId).getOrElse(fail()).movable shouldBe true
        db.remove(cart).getOrElse(fail())
      }
    }

    describe("after being associated to a customer") {
      it("should show the associated customer in the database") {
        val db: Repository = repository.getOrElse(fail())
        val cart: Cart = db.add(store).getOrElse(fail())
        db.findById(cart.cartId).getOrElse(fail()).movable shouldBe false
        db.update(AssociatedCart(cart.cartId, store, customer)).getOrElse(fail())
        val foundCart: Cart = db.findById(cart.cartId).getOrElse(fail())
        foundCart match {
          case c: AssociatedCart =>
            c.movable shouldBe true
            c.customer shouldBe customer
          case _ => fail()
        }
        db.remove(cart).getOrElse(fail())
      }
    }

    describe("after being associated then locked again") {
      it("should show as locked in the database") {
        val db: Repository = repository.getOrElse(fail())
        val cart: Cart = db.add(store).getOrElse(fail())
        db.update(AssociatedCart(cart.cartId, store, customer)).getOrElse(fail())
        db.update(LockedCart(cart.cartId, store)).getOrElse(fail())
        db.findById(cart.cartId).getOrElse(fail()).movable shouldBe false
        db.remove(cart).getOrElse(fail())
      }
    }

    describe("when queried to retrieve all carts registered with a store") {
      it("should return all the requested carts") {
        val db: Repository = repository.getOrElse(fail())
        val cartNotInStore: Cart = db.add(Store(9999).getOrElse(fail())).getOrElse(fail())
        val firstCart: Cart = db.add(store).getOrElse(fail())
        val secondCart: Cart = db.add(store).getOrElse(fail())
        val thirdCart: Cart = db.add(store).getOrElse(fail())

        db.findByStore(store).getOrElse(fail()).contains(cartNotInStore) shouldBe false
        db.findByStore(store).getOrElse(fail()) shouldBe Set(firstCart, secondCart, thirdCart)

        db.remove(cartNotInStore).getOrElse(fail())
        db.remove(firstCart).getOrElse(fail())
        db.remove(secondCart).getOrElse(fail())
        db.remove(thirdCart).getOrElse(fail())
      }
    }
  }
}
