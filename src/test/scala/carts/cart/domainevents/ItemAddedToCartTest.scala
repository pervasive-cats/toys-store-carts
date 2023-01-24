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

import carts.cart.valueobjects.{Customer, Store}
import carts.cart.valueobjects.item.{CatalogItem, ItemId}

class ItemAddedToCartTest extends AnyFunSpec {

  describe("An item added to cart event") {
    describe("when created with a customer, store, catalog item, item identifier") {
      it("should contain them") {
        val customer: Customer = Customer("addr_3ss!@email.com").getOrElse(fail())
        val store: Store = Store(0).getOrElse(fail())
        val catalogItem: CatalogItem = CatalogItem(2).getOrElse(fail())
        val itemId: ItemId = ItemId(10).getOrElse(fail())
        val event = ItemAddedToCart(customer, store, catalogItem, itemId)

        event.customer shouldBe customer
        event.store shouldBe store
        event.catalogItem shouldBe catalogItem
        event.itemId shouldBe itemId
      }
    }
  }
}
