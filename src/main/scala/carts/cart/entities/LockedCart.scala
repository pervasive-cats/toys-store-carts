/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.{CartId, Customer, Store}

trait LockedCart extends Cart

object LockedCart {

  private case class LockedCartImpl(cartId: CartId, store: Store, isMovable: Boolean) extends LockedCart

  given LockedCartOps[LockedCart] with {

    override def unlock(lockedCart: LockedCart): UnlockedCart = UnlockedCart(lockedCart.cartId, lockedCart.store)

    override def associateTo(lockedCart: LockedCart, customer: Customer): AssociatedCart =
      AssociatedCart(lockedCart.cartId, lockedCart.store, customer)
  }

  def apply(cartId: CartId, store: Store): LockedCart = LockedCartImpl(cartId, store, isMovable = false)
}
