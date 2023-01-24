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

class ItemTest extends AnyFunSpec {

  describe("An item") {
    describe("when created with valid catalog item and item identifiers") {
      it("should be created correctly") {
        val catalogItemId: Long = 9000
        val itemIdValue: Long = 4

        val item = Item(CatalogItem(catalogItemId).getOrElse(fail()), ItemId(itemIdValue).getOrElse(fail()))

        (item.catalogItem.value: Long) shouldBe catalogItemId
        (item.itemId.value: Long) shouldBe itemIdValue
      }
    }
  }
}
