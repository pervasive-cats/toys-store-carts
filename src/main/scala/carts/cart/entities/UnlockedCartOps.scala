/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.Customer

trait UnlockedCartOps[A <: UnlockedCart] {

  def lock(unlockedCart: UnlockedCart): LockedCart

  def associateTo(unlockedCart: UnlockedCart, customer: Customer): AssociatedCart
}

object UnlockedCartOps {

  extension [A <: UnlockedCart: UnlockedCartOps](unlockedCart: A) {

    def lock: LockedCart = implicitly[UnlockedCartOps[A]].lock(unlockedCart)

    def associateTo(customer: Customer): AssociatedCart = implicitly[UnlockedCartOps[A]].associateTo(unlockedCart, customer)
  }
}
