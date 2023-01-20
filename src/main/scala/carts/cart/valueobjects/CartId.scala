package io.github.pervasivecats
package carts.cart.valueobjects

import carts.id

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

trait CartId {

  val value: id

}
