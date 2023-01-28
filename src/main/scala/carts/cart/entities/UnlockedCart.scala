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

trait UnlockedCart extends Cart

object UnlockedCart {

  private case class UnlockedCartImpl(cartId: CartId, store: Store, movable: Boolean) extends UnlockedCart {

    override def toString: String = s"UnlockedCart(${cartId.value}, ${store.value})"

    override def equals(obj: Any): Boolean = cartsEquals(obj)(cartId, store)

    override def hashCode(): Int = cartId.## + store.##
  }

  given UnlockedCartOps[UnlockedCart] with {

    override def lock(unlockedCart: UnlockedCart): LockedCart = LockedCart(unlockedCart.cartId, unlockedCart.store)

    override def associateTo(unlockedCart: UnlockedCart, customer: Customer): AssociatedCart =
      AssociatedCart(unlockedCart.cartId, unlockedCart.store, customer)
  }

  def apply(cartId: CartId, store: Store): UnlockedCart = UnlockedCartImpl(cartId, store, movable = true)
}
