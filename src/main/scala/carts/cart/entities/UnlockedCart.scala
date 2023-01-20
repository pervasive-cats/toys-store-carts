package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.Customer

trait UnlockedCart extends Cart {

  def lock: LockedCart
  
  def associateTo(customer: Customer): AssociatedCart

}
