package io.github.pervasivecats
package carts.cart.services

import carts.cart.events.{CartMoved, ItemInsertedIntoCart}

trait CartMovementHandlers {

  def onCartMoved(event: CartMoved): Unit
}
