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

trait AssociatedCart extends Cart {

  val customer: Customer
}

object AssociatedCart {

  private case class AssociatedCartImpl(cartId: CartId, store: Store, movable: Boolean, customer: Customer)
    extends AssociatedCart {

    override def equals(obj: Any): Boolean = cartsEquals(obj)(cartId, store)

    override def hashCode(): Int = Objects.hash(cartId, store)
  }

  given AssociatedCartOps[AssociatedCart] with {

    override def lock(associatedCart: AssociatedCart): LockedCart = LockedCart(associatedCart.cartId, associatedCart.store)
  }

  def apply(cartId: CartId, store: Store, customer: Customer): AssociatedCart =
    AssociatedCartImpl(cartId: CartId, store: Store, movable = true, customer: Customer)
}
