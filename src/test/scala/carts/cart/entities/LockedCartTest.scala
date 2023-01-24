/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */
package io.github.pervasivecats
package carts.cart.entities

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import carts.cart.valueobjects.{CartId, Customer, Store}
import carts.cart.entities.LockedCartOps.*

class LockedCartTest extends AnyFunSpec {

  private val cartId: CartId = CartId(9).getOrElse(fail())
  private val store: Store = Store(0).getOrElse(fail())
  private val lockedCart = LockedCart(cartId, store)

  describe("A locked cart") {
    describe("when created with a cart identifier and a store") {
      it("should contain them") {
        lockedCart.cartId shouldBe cartId
        lockedCart.store shouldBe store
        lockedCart.isMovable shouldBe false
      }
    }

    describe("when unlocked") {
      it("should return itself as an unlocked cart") {
        val unlockedCart = lockedCart.unlock

        unlockedCart.cartId shouldBe cartId
        unlockedCart.store shouldBe store
        unlockedCart.isMovable shouldBe true
      }
    }

    describe("when associated to a customer") {
      it("should return itself as a cart associated to the given customer") {
        val customer: Customer = Customer("addr_3ss!@email.com").getOrElse(fail())
        val associatedCart = lockedCart.associateTo(customer)

        associatedCart.cartId shouldBe cartId
        associatedCart.store shouldBe store
        associatedCart.isMovable shouldBe true
        associatedCart.customer shouldBe customer
      }
    }
  }
}
