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

import carts.cart.valueobjects.item.CatalogItem.WrongCatalogItemFormat

class CatalogItemTest extends AnyFunSpec {

  describe("A catalog item") {
    describe("when created with a negative value identifier") {
      it("should not be valid") {
        CatalogItem(-9000).left.value shouldBe WrongCatalogItemFormat
      }
    }

    describe("when created with a positive value identifier") {
      it("should be valid") {
        val id: Long = 9000

        (CatalogItem(id).value.value: Long) shouldBe id
      }
    }

    describe("when created with an identifier of value 0") {
      it("should be valid") {
        val id: Long = 0

        (CatalogItem(id).value.value: Long) shouldBe id
      }
    }
  }
}
