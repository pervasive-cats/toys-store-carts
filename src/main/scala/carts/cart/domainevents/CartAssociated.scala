/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.domainevents

import carts.cart.valueobjects.{CartId, Customer, Store}

trait CartAssociated {

  val cartId: CartId

  val store: Store

  val customer: Customer
}

object CartAssociated {

  private case class CartAssociatedImpl(cartId: CartId, store: Store, customer: Customer) extends CartAssociated

  def apply(cartId: CartId, store: Store, customer: Customer): CartAssociated = CartAssociatedImpl(cartId, store, customer)
}
