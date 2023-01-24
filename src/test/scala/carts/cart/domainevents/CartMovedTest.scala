/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.domainevents

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import carts.cart.valueobjects.{CartId, Store}

class CartMovedTest extends AnyFunSpec {

  describe("A cart moved event") {
    describe("when created with a cart id and a store") {
      it("should contain them") {
        val cartId: CartId = CartId(9).getOrElse(fail())
        val store: Store = Store(0).getOrElse(fail())
        val event = CartMoved(cartId, store)

        event.cartId shouldBe cartId
        event.store shouldBe store
      }
    }
  }
}
