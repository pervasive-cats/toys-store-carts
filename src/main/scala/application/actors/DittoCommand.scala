/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import carts.cart.valueobjects.{CartId, Store}

sealed trait DittoCommand

object DittoCommand {

  final case class RaiseCartAlarm(cartId: CartId, store: Store) extends DittoCommand
}
