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

import carts.cart.valueobjects.{CartId, Customer, Store}
import carts.cart.valueobjects.item.{CatalogItem, ItemId}

class CartAssociatedTest extends AnyFunSpec {

  describe("A cart associated event") {
    describe("when created with a customer, store, catalog item, item identifier") {
      it("should contain them") {
        val customer: Customer = Customer("addr_3ss!@email.com").getOrElse(fail())
        val store: Store = Store(0).getOrElse(fail())
        val cartId: CartId = CartId(10).getOrElse(fail())
        val event = CartAssociated(cartId, store, customer)

        event.customer shouldBe customer
        event.store shouldBe store
        event.cartId shouldBe cartId
      }
    }
  }
}
