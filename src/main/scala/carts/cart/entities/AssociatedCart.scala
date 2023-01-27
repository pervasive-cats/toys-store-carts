/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.{CartId, Customer, Store}

trait AssociatedCart extends Cart {

  val customer: Customer
}

object AssociatedCart {

  private case class AssociatedCartImpl(cartId: CartId, store: Store, movable: Boolean, customer: Customer)
    extends AssociatedCart

  given AssociatedCartOps[AssociatedCart] with {

    override def lock(associatedCart: AssociatedCart): LockedCart = LockedCart(associatedCart.cartId, associatedCart.store)
  }

  def apply(cartId: CartId, store: Store, customer: Customer): AssociatedCart =
    AssociatedCartImpl(cartId: CartId, store: Store, movable = true, customer: Customer)
}
