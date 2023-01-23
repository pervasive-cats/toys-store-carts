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

import carts.cart.valueobjects.Store.WrongStoreFormat

class StoreTest extends AnyFunSpec {

  describe("A store") {
    describe("when created with a negative value identifier") {
      it("should not be valid") {
        Store(-9000).left.value shouldBe WrongStoreFormat
      }
    }

    describe("when created with a positive value identifier") {
      it("should be valid") {
        val storeId: Long = 9000

        (Store(storeId).value.value: Long) shouldBe storeId
      }
    }

    describe("when created with an identifier of value 0") {
      it("should be valid") {
        val storeId: Long = 0

        (Store(storeId).value.value: Long) shouldBe storeId
      }
    }
  }
}
