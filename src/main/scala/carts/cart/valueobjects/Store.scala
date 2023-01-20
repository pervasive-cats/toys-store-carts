/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.valueobjects

import io.github.pervasivecats.carts.id

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

trait Store {

  val value: id

}
