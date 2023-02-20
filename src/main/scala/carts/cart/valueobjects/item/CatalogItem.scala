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

trait CatalogItem {

  val value: Id
}

object CatalogItem {

  private case class CatalogItemImpl(value: Id) extends CatalogItem

  case object WrongCatalogItemFormat extends ValidationError {

    override val message: String = "The catalog item format is invalid"
  }

  def apply(value: Long): Validated[CatalogItem] = applyRef[Id](value) match {
    case Left(_) => Left[ValidationError, CatalogItem](WrongCatalogItemFormat)
    case Right(value) => Right[ValidationError, CatalogItem](CatalogItemImpl(value))
  }
}
