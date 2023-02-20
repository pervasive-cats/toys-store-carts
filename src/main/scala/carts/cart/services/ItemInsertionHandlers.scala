/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.services

import akka.actor.typed.ActorRef

import application.actors.{DittoCommand, MessageBrokerCommand}
import application.actors.DittoCommand.RaiseCartAlarm
import application.actors.MessageBrokerCommand.ItemAddedToCart
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
