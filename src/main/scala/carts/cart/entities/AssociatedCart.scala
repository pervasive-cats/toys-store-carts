package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.Customer

trait AssociatedCart extends Cart {
  
  val customer: Customer
  
  def lock: LockedCart

}
