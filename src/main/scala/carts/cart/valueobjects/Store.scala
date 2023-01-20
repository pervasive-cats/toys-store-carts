package io.github.pervasivecats
package carts.cart.valueobjects

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative
import io.github.pervasivecats.carts.id

trait Store {

  val value: id

}
