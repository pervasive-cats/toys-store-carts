/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors.commands

import akka.actor.typed.ActorRef

import application.routes.entities.Response.*
import carts.cart.valueobjects.*

sealed trait CartServerCommand

object CartServerCommand {

  final case class AssociateCart(cartId: CartId, store: Store, customer: Customer, replyTo: ActorRef[CartResponse])
    extends CartServerCommand

  final case class UnlockCart(cartId: CartId, store: Store, replyTo: ActorRef[CartResponse]) extends CartServerCommand

  final case class LockCart(cartId: CartId, store: Store, replyTo: ActorRef[CartResponse]) extends CartServerCommand

  final case class AddCart(store: Store, replyTo: ActorRef[CartResponse]) extends CartServerCommand

  final case class RemoveCart(cartId: CartId, store: Store, replyTo: ActorRef[EmptyResponse]) extends CartServerCommand

  final case class ShowAllCarts(store: Store, replyTo: ActorRef[StoreCartsResponse]) extends CartServerCommand
}
