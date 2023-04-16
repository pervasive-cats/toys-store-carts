/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.services

import io.github.pervasivecats.application.actors.commands.DittoCommand
import io.github.pervasivecats.application.actors.commands.DittoCommand.RaiseCartAlarm

import akka.actor.typed.ActorRef

import carts.cart.domainevents.CartMoved
import carts.cart.Repository
import carts.cart.entities.*

trait CartMovementHandlers {

  def onCartMoved(event: CartMoved)(using Repository): Validated[Unit]
}

object CartMovementHandlers {

  private class CartMovementHandlersImpl(dittoActor: ActorRef[DittoCommand]) extends CartMovementHandlers {

    override def onCartMoved(event: CartMoved)(using Repository): Validated[Unit] =
      summon[Repository].findById(event.cartId, event.store).map {
        case _: LockedCart => dittoActor ! RaiseCartAlarm(event.cartId, event.store)
        case _ => ()
      }
  }

  def apply(dittoActor: ActorRef[DittoCommand]): CartMovementHandlers = CartMovementHandlersImpl(dittoActor)
}
