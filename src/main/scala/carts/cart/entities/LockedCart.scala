package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.Customer

trait LockedCart extends Cart {

  def unlock: UnlockedCart

  def associateTo(customer: Customer): AssociatedCart
  
}
