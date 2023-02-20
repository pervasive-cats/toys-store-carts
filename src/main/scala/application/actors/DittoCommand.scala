/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import carts.cart.valueobjects.{CartId, Store}
import carts.cart.valueobjects.item.{CatalogItem, ItemId}

sealed trait DittoCommand

object DittoCommand {

  final case class ItemInsertedIntoCart(cartId: CartId, store: Store, catalogItem: CatalogItem, itemId: ItemId)
    extends DittoCommand

  final case class CartMoved(cartId: CartId, store: Store) extends DittoCommand

  final case class RaiseCartAlarm(cartId: CartId, store: Store) extends DittoCommand
}
