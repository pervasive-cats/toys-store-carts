/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.entities

import AnyOps.===

import Cart.cartsEquals
import carts.cart.valueobjects.{CartId, Customer, Store}

import java.util.Objects

trait LockedCart extends Cart

object LockedCart {

  private case class LockedCartImpl(cartId: CartId, store: Store, movable: Boolean) extends LockedCart {

    override def equals(obj: Any): Boolean = cartsEquals(obj)(cartId, store)

    override def hashCode(): Int = Objects.hash(cartId, store)
  }

  given LockedCartOps[LockedCart] with {

    override def unlock(lockedCart: LockedCart): UnlockedCart = UnlockedCart(lockedCart.cartId, lockedCart.store)

    override def associateTo(lockedCart: LockedCart, customer: Customer): AssociatedCart =
      AssociatedCart(lockedCart.cartId, lockedCart.store, customer)
  }

  def apply(cartId: CartId, store: Store): LockedCart = LockedCartImpl(cartId, store, movable = false)
}
