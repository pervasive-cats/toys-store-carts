/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.domainevents

import carts.cart.valueobjects.{CartId, Store}
import carts.cart.valueobjects.item.{CatalogItem, ItemId}

trait ItemInsertedIntoCart {

  val cartId: CartId

  val store: Store

  val catalogItem: CatalogItem

  val itemId: ItemId
}

object ItemInsertedIntoCart {

  private case class ItemInsertedIntoCartImpl(cartId: CartId, store: Store, catalogItem: CatalogItem, itemId: ItemId)
    extends ItemInsertedIntoCart

  def apply(cartId: CartId, store: Store, catalogItem: CatalogItem, itemId: ItemId): ItemInsertedIntoCart =
    ItemInsertedIntoCartImpl(cartId, store, catalogItem, itemId)
}
