/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.events

import io.github.pervasivecats.carts.cart.valueobjects.item.CatalogItem
import io.github.pervasivecats.carts.cart.valueobjects.item.ItemId

import carts.cart.valueobjects.{CartId, Store}

trait ItemInsertedIntoCart {

  val cartId: CartId
  val store: Store
  val catalogItem: CatalogItem
  val itemId: ItemId

}
