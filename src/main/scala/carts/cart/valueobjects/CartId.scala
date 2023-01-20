package io.github.pervasivecats
package carts.cart.valueobjects

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

type id = Long Refined NonNegative

trait CartId {

  val value: id

}
