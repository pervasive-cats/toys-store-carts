/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.Customer

trait LockedCartOps[A <: LockedCart] {

  def unlock(lockedCart: A): UnlockedCart

  def associateTo(lockedCart: A, customer: Customer): AssociatedCart
}

object LockedCartOps {

  extension [A <: LockedCart: LockedCartOps](lockedCart: A) {

    def unlock: UnlockedCart = implicitly[LockedCartOps[A]].unlock(lockedCart)

    def associateTo(customer: Customer): AssociatedCart = implicitly[LockedCartOps[A]].associateTo(lockedCart, customer)
  }
}
