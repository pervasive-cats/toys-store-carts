/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.valueobjects.item

trait Item {

  val catalogItem: CatalogItem

  val itemId: ItemId
}

object Item {

  private case class ItemImpl(catalogItem: CatalogItem, itemId: ItemId) extends Item

  def apply(catalogItem: CatalogItem, itemId: ItemId): Item = ItemImpl(catalogItem, itemId)
}
