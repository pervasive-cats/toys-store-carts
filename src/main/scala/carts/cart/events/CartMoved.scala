package io.github.pervasivecats
package carts.cart.events

import carts.cart.valueobjects.{CartId, Store}

trait CartMoved {
  
  val cartId: CartId
  val store: Store 

}
