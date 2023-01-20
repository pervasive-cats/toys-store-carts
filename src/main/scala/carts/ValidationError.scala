package io.github.pervasivecats
package carts

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

type Validated[A] = Either[ValidationError, A]

type id = Long Refined NonNegative

trait ValidationError {

  val message: String
}
