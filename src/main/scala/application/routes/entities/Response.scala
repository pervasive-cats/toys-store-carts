/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes.entities

import carts.cart.entities.Cart

sealed trait Response[A] {

  val result: Validated[A]
}

object Response {

  final case class CartResponse(result: Validated[Cart]) extends Response[Cart]

  final case class StoreCartsResponse(result: Validated[Set[Validated[Cart]]]) extends Response[Set[Validated[Cart]]]

  final case class EmptyResponse(result: Validated[Unit]) extends Response[Unit]
}
