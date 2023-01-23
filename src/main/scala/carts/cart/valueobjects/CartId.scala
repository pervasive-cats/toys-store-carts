/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.valueobjects

import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

import carts.{Id, Validated, ValidationError}

trait CartId {

  val value: Id
}

object CartId {

  private case class CartIdImpl(value: Id) extends CartId

  case object WrongCartIdFormat extends ValidationError {

    override val message: String = "The cart id format is invalid"
  }

  def apply(value: Long): Validated[CartId] = applyRef[Id](value) match {
    case Left(_) => Left[ValidationError, CartId](WrongCartIdFormat)
    case Right(value) => Right[ValidationError, CartId](CartIdImpl(value))
  }
}
