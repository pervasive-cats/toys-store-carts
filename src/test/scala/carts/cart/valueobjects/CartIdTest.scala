/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.valueobjects

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import carts.cart.valueobjects.CartId.WrongCartIdFormat

class CartIdTest extends AnyFunSpec {

  describe("A cart id") {
    describe("when created with a negative value identifier") {
      it("should not be valid") {
        CartId(-9000).left.value shouldBe WrongCartIdFormat
      }
    }

    describe("when created with a positive value identifier") {
      it("should be valid") {
        val cartId: Long = 9000

        (CartId(cartId).value.value: Long) shouldBe cartId
      }
    }

    describe("when created with an identifier of value 0") {
      it("should be valid") {
        val cartId: Long = 0

        (CartId(cartId).value.value: Long) shouldBe cartId
      }
    }
  }
}
