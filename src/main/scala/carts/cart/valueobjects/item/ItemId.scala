/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.valueobjects.item

import io.github.pervasivecats.Validated
import io.github.pervasivecats.ValidationError

import eu.timepit.refined.api.RefType.applyRef

import carts.Id

trait ItemId {

  val value: Id
}

object ItemId {

  private case class ItemIdImpl(value: Id) extends ItemId

  case object WrongItemIdFormat extends ValidationError {

    override val message: String = "The item id format is invalid"
  }

  def apply(value: Long): Validated[ItemId] = applyRef[Id](value) match {
    case Left(_) => Left[ValidationError, ItemId](WrongItemIdFormat)
    case Right(value) => Right[ValidationError, ItemId](ItemIdImpl(value))
  }
}
