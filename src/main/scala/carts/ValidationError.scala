/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts

type Validated[A] = Either[ValidationError, A]

trait ValidationError {

  val message: String
}
