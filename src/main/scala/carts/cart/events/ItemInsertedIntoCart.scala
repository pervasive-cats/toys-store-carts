package io.github.pervasivecats
package carts.cart.events

import carts.cart.valueobjects.{CartId, Store}

import io.github.pervasivecats.carts.cart.valueobjects.item.{CatalogItem, ItemId}

trait ItemInsertedIntoCart {

  val cartId: CartId
  val store: Store
  val catalogItem: CatalogItem
  val itemId: ItemId

}
