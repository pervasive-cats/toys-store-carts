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

class CartTest extends AnyFunSpec {

  private val cartId: CartId = CartId(0).getOrElse(fail())
  private val store: Store = Store(1).getOrElse(fail())
  private val customer: Customer = Customer("addr3ss@mail.com").getOrElse(fail())
  private val lockedCart: LockedCart = LockedCart(cartId, store)
  private val unlockedCart: UnlockedCart = UnlockedCart(cartId, store)
  private val associatedCart: AssociatedCart = AssociatedCart(cartId, store, customer)
  private val differentCart: LockedCart = LockedCart(CartId(100).getOrElse(fail()), Store(100).getOrElse(fail()))

  describe("A cart") {
    describe("with the same identifier and store combination as another cart") {
      it("should be equal to that cart") {
        lockedCart shouldBe lockedCart
        lockedCart shouldBe unlockedCart
        lockedCart shouldBe associatedCart
        unlockedCart shouldBe unlockedCart
        unlockedCart shouldBe associatedCart
        associatedCart shouldBe associatedCart
      }
    }

    describe("with identifier and store combination different from another cart") {
      it("should not be equal to that cart") {
        lockedCart should not be differentCart
        unlockedCart should not be differentCart
        associatedCart should not be differentCart
      }
    }
  }
}
