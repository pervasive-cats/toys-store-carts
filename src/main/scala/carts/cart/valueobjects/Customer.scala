/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.valueobjects

import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.MatchesRegex

import carts.{Validated, ValidationError}

type EmailString = String Refined
  MatchesRegex[
    "[a-z0-9!#$%&'*+/=?^_`{|}~\\-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~\\-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"
  ]

trait Customer {

  val value: EmailString
}

object Customer {

  private case class CustomerImpl(value: EmailString) extends Customer

  case object WrongCustomerFormat extends ValidationError {

    override val message: String = "The email format is invalid"
  }

  def apply(value: String): Validated[Customer] = applyRef[EmailString](value) match {
    case Left(_) => Left[ValidationError, Customer](WrongCustomerFormat)
    case Right(value) => Right[ValidationError, Customer](CustomerImpl(value))
  }
}
