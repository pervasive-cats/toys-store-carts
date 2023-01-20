package io.github.pervasivecats
package carts.cart.services

import carts.cart.events.CustomerUnregistered

trait CustomerStateHandlers {
  
  def onCustomerUnregistered(event: CustomerUnregistered): Unit

}
