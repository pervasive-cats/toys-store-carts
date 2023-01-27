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
        db.add(LockedCart(cartId, store)).getOrElse(fail())
        val cart: Cart = db.findById(cartId, store).getOrElse(fail())
        cart.cartId shouldBe cartId
        cart.store shouldBe store
        cart.movable shouldBe false
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

    describe("if already present in the database") {
      it("should not allow a new registration") {
        val db: Repository = repository.getOrElse(fail())
        val lockedCart: Cart = LockedCart(cartId, store)
        db.add(lockedCart).getOrElse(fail())
        db.add(lockedCart).left.value shouldBe CartAlreadyPresent
        db.remove(lockedCart).getOrElse(fail())
      }
    }

    describe("if added with the same cart identifier as another cart, but different store") {
      it("should be added to the database") {
        val db: Repository = repository.getOrElse(fail())
        val firstCart = LockedCart(CartId(0).getOrElse(fail()), Store(0).getOrElse(fail()))
        val secondCart = LockedCart(CartId(0).getOrElse(fail()), Store(1).getOrElse(fail()))
        db.add(firstCart).getOrElse(fail())
        db.add(secondCart).getOrElse(fail())
        db.remove(firstCart).getOrElse(fail())
        db.remove(secondCart).getOrElse(fail())
      }
    }

    describe("if never added to the database") {
      it("should not be present") {
        val db: Repository = repository.getOrElse(fail())
        db.findById(cartId, store).left.value shouldBe CartNotFound
      }
      it("should not be updatable") {
        val db: Repository = repository.getOrElse(fail())
        db.update(LockedCart(cartId, store)).left.value shouldBe OperationFailed
      }
      it("should not be removable") {
        val db: Repository = repository.getOrElse(fail())
        db.remove(LockedCart(cartId, store)).left.value shouldBe OperationFailed
      }
    }

    describe("after being unlocked") {
      it("should show as unlocked in the database") {
        val db: Repository = repository.getOrElse(fail())
        db.add(LockedCart(cartId, store)).getOrElse(fail())
        db.findById(cartId, store).getOrElse(fail()).movable shouldBe false
        db.update(UnlockedCart(cartId, store)).getOrElse(fail())
        db.findById(cartId, store).getOrElse(fail()).movable shouldBe true
        db.remove(UnlockedCart(cartId, store)).getOrElse(fail())
      }
    }

    describe("after being associated to a customer") {
      it("should show the associated customer in the database") {
        val db: Repository = repository.getOrElse(fail())
        db.add(LockedCart(cartId, store)).getOrElse(fail())
        db.findById(cartId, store).getOrElse(fail()).movable shouldBe false
        db.update(AssociatedCart(cartId, store, customer)).getOrElse(fail())
        val cart: Cart = db.findById(cartId, store).getOrElse(fail())
        cart match {
          case cart: AssociatedCart =>
            cart.movable shouldBe true
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
        db.findById(cartId, store).getOrElse(fail()).movable shouldBe false
        db.remove(LockedCart(cartId, store)).getOrElse(fail())
      }
    }

    describe("when queried to retrieve all carts registered with a store") {
      it("should return all the requested carts") {
        val db: Repository = repository.getOrElse(fail())
        val cartNotInStore: LockedCart = LockedCart(CartId(0).getOrElse(fail()), Store(0).getOrElse(fail()))
        val lockedCart: LockedCart = LockedCart(CartId(0).getOrElse(fail()), store)
        val unlockedCartId: Long = 1
        val unlockedCart: UnlockedCart = UnlockedCart(CartId(unlockedCartId).getOrElse(fail()), store)
        val associatedCartId: Long = 2
        val associatedCart: AssociatedCart = AssociatedCart(CartId(associatedCartId).getOrElse(fail()), store, customer)

        db.add(cartNotInStore).getOrElse(fail())
        db.add(lockedCart).getOrElse(fail())
        db.add(LockedCart(CartId(unlockedCartId).getOrElse(fail()), store)).getOrElse(fail())
        db.update(unlockedCart).getOrElse(fail())
        db.add(LockedCart(CartId(associatedCartId).getOrElse(fail()), store)).getOrElse(fail())
        db.update(associatedCart).getOrElse(fail())

        db.findByStore(store).getOrElse(fail()).contains(cartNotInStore) shouldBe false
        db.findByStore(store).getOrElse(fail()) shouldBe Set(lockedCart, unlockedCart, associatedCart)

        db.remove(cartNotInStore).getOrElse(fail())
        db.remove(lockedCart).getOrElse(fail())
        db.remove(unlockedCart).getOrElse(fail())
        db.remove(associatedCart).getOrElse(fail())
      }
    }
  }
}
