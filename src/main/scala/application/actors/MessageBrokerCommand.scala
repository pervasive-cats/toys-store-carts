/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import akka.actor.typed.ActorRef

import carts.cart.domainevents.{ItemAddedToCart as ItemAddedToCartEvent, CartAssociated as CartAssociatedEvent}

sealed trait MessageBrokerCommand

object MessageBrokerCommand {

  final case class ItemAddedToCart(event: ItemAddedToCartEvent) extends MessageBrokerCommand

  final case class CartAssociated(event: CartAssociatedEvent) extends MessageBrokerCommand
}
