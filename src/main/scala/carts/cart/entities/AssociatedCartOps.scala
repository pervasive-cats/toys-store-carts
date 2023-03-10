/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.Customer

trait AssociatedCartOps[A <: AssociatedCart] {

  def lock(associatedCart: AssociatedCart): LockedCart
}

object AssociatedCartOps {

  extension [A <: AssociatedCart: AssociatedCartOps](associatedCart: A) {

    def lock: LockedCart = implicitly[AssociatedCartOps[A]].lock(associatedCart)
  }
}
