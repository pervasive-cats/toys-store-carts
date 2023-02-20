/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */
package io.github.pervasivecats
package carts.cart.valueobjects.item

import io.github.pervasivecats.carts.cart.valueobjects.Store

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

class ItemTest extends AnyFunSpec {

  describe("An item") {
    describe("when created with a catalog item and an item identifiers") {
      it("should contain them") {
        val catalogItemId: Long = 9000
        val store: Long = 1000
        val itemIdValue: Long = 4
        val item = Item(
          CatalogItem(catalogItemId).getOrElse(fail()),
          Store(store).getOrElse(fail()),
          ItemId(itemIdValue).getOrElse(fail())
        )
        (item.catalogItem.value: Long) shouldBe catalogItemId
        (item.store.value: Long) shouldBe store
        (item.itemId.value: Long) shouldBe itemIdValue
      }
    }
  }
}
