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
