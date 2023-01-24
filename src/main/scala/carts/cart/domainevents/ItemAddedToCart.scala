/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.domainevents

import carts.cart.valueobjects.{CartId, Customer, Store}
import carts.cart.valueobjects.item.{CatalogItem, ItemId}

trait ItemAddedToCart {

  val customer: Customer

  val store: Store

  val catalogItem: CatalogItem

  val itemId: ItemId
}

object ItemAddedToCart {

  private case class ItemAddedToCartImpl(customer: Customer, store: Store, catalogItem: CatalogItem, itemId: ItemId)
    extends ItemAddedToCart

  def apply(customer: Customer, store: Store, catalogItem: CatalogItem, itemId: ItemId): ItemAddedToCart =
    ItemAddedToCartImpl(customer, store, catalogItem, itemId)
}
