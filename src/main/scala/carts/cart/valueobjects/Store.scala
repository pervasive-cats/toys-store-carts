/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.valueobjects

import io.github.pervasivecats.Validated
import io.github.pervasivecats.ValidationError

import eu.timepit.refined.api.RefType.applyRef

import carts.Id

trait Store {

  val value: Id
}

object Store {

  private case class StoreImpl(value: Id) extends Store

  case object WrongStoreFormat extends ValidationError {

    override val message: String = "The store format is invalid"
  }

  def apply(value: Long): Validated[Store] = applyRef[Id](value) match {
    case Left(_) => Left[ValidationError, Store](WrongStoreFormat)
    case Right(value) => Right[ValidationError, Store](StoreImpl(value))
  }
}
