/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */
package io.github.pervasivecats
package carts.cart.valueobjects.item

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import carts.cart.valueobjects.item.ItemId.WrongItemIdFormat

class ItemIdTest extends AnyFunSpec {

  describe("An item id") {
    describe("when created with a negative value") {
      it("should not be valid") {
        ItemId(-9000).left.value shouldBe WrongItemIdFormat
      }
    }

    describe("when created with a positive value") {
      it("should be valid") {
        val id: Long = 9000

        (ItemId(id).value.value: Long) shouldBe id
      }
    }

    describe("when created with a value of 0") {
      it("should be valid") {
        val id: Long = 0

        (ItemId(id).value.value: Long) shouldBe id
      }
    }
  }
}
