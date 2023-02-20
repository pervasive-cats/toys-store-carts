/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.valueobjects.item

import carts.cart.valueobjects.Store

trait Item {

  val catalogItem: CatalogItem

  val store: Store

  val itemId: ItemId
}

object Item {

  private case class ItemImpl(catalogItem: CatalogItem, store: Store, itemId: ItemId) extends Item

  def apply(catalogItem: CatalogItem, store: Store, itemId: ItemId): Item = ItemImpl(catalogItem, store, itemId)
}
