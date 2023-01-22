package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.Customer

trait AssociatedCartOps {

  val customer: Customer

  def lock: LockedCart
}
