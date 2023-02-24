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
import carts.cart.entities.AssociatedCartOps.*

class AssociatedCartTest extends AnyFunSpec {

  private val cartId: CartId = CartId(9).getOrElse(fail())
  private val store: Store = Store(0).getOrElse(fail())
  private val customer: Customer = Customer("addr_3ss!@email.com").getOrElse(fail())
  private val associatedCart = AssociatedCart(cartId, store, customer)

  describe("A cart associated to a customer") {
    describe("when created with a cart identifier, a store and a customer") {
      it("should contain them") {
        associatedCart.cartId shouldBe cartId
        associatedCart.store shouldBe store
        associatedCart.movable shouldBe true
        associatedCart.customer shouldBe customer
      }
    }

    describe("when locked") {
      it("should return itself as a locked cart") {
        val lockedCart = associatedCart.lock

        lockedCart.cartId shouldBe cartId
        lockedCart.store shouldBe store
        lockedCart.movable shouldBe false
      }
    }
  }
}
