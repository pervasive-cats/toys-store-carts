package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.{CartId, Store}

trait Cart {

  val cartId: CartId
  val store: Store
  val isMovable: Boolean

}
