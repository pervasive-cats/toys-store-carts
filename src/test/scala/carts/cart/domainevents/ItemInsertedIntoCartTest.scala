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

import carts.cart.valueobjects.item.{CatalogItem, ItemId}
import carts.cart.valueobjects.{CartId, Store}

class ItemInsertedIntoCartTest extends AnyFunSpec {

  describe("An item inserted into cart event") {
    describe("when created with a cart identifier, a store, a catalog item and an item identifier") {
      it("should contain them") {
        val cartId: CartId = CartId(1).getOrElse(fail())
        val store: Store = Store(0).getOrElse(fail())
        val catalogItem: CatalogItem = CatalogItem(2).getOrElse(fail())
        val itemId: ItemId = ItemId(10).getOrElse(fail())
        val event = ItemInsertedIntoCart(cartId, store, catalogItem, itemId)

        event.cartId shouldBe cartId
        event.store shouldBe store
        event.catalogItem shouldBe catalogItem
        event.itemId shouldBe itemId
      }
    }
  }
}
