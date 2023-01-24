/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.domainevents

import carts.cart.valueobjects.{CartId, Store}

trait CartMoved {

  val cartId: CartId

  val store: Store
}

object CartMoved {

  private case class CartMovedImpl(cartId: CartId, store: Store) extends CartMoved

  def apply(cartId: CartId, store: Store): CartMoved = CartMovedImpl(cartId, store)
}
