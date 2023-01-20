package io.github.pervasivecats
package carts.cart.valueobjects.item

import carts.id

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

trait CatalogItem {

  val value: id
}
