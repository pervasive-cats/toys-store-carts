package io.github.pervasivecats
package carts

type Validated[A] = Either[ValidationError, A]

trait ValidationError {

  val message: String
}
