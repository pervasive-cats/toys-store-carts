package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.Customer

trait LockedCartOps[A <: LockedCart] {

  def unlock: UnlockedCart

  def associateTo(customer: Customer): AssociatedCart
}
