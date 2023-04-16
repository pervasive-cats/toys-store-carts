/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.services

import io.github.pervasivecats.application.actors.commands.DittoCommand
import io.github.pervasivecats.application.actors.commands.DittoCommand.RaiseCartAlarm
import io.github.pervasivecats.application.actors.commands.MessageBrokerCommand
import io.github.pervasivecats.application.actors.commands.MessageBrokerCommand.ItemAddedToCart

import akka.actor.typed.ActorRef

import carts.cart.domainevents.{ItemInsertedIntoCart, ItemAddedToCart as ItemAddedToCartEvent}
import carts.cart.Repository
import carts.cart.entities.*

trait ItemInsertionHandlers {

  def onItemInsertedIntoCart(event: ItemInsertedIntoCart)(using Repository): Validated[Unit]
}

object ItemInsertionHandlers {

  private class ItemInsertionHandlersImpl(messageBrokerActor: ActorRef[MessageBrokerCommand], dittoActor: ActorRef[DittoCommand])
    extends ItemInsertionHandlers {

    override def onItemInsertedIntoCart(event: ItemInsertedIntoCart)(using Repository): Validated[Unit] =
      summon[Repository].findById(event.cartId, event.store).map {
        case cart: AssociatedCart =>
          messageBrokerActor ! ItemAddedToCart(
            ItemAddedToCartEvent(cart.customer, event.store, event.catalogItem, event.itemId)
          )
        case _ => dittoActor ! RaiseCartAlarm(event.cartId, event.store)
      }
  }

  def apply(messageBrokerActor: ActorRef[MessageBrokerCommand], dittoActor: ActorRef[DittoCommand]): ItemInsertionHandlers =
    ItemInsertionHandlersImpl(messageBrokerActor, dittoActor)
}
