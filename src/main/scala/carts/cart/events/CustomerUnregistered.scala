package io.github.pervasivecats
package carts.cart.events

import carts.cart.valueobjects.Customer

trait CustomerUnregistered {

  val customer: Customer

}
