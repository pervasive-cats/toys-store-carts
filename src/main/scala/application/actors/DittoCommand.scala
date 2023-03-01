/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import akka.actor.typed.ActorRef
import org.eclipse.ditto.client.DittoClient

import carts.cart.valueobjects.{CartId, Customer, Store}
import carts.cart.valueobjects.item.{CatalogItem, ItemId}

sealed trait DittoCommand

object DittoCommand {

  final case class DittoClientConnected(client: DittoClient) extends DittoCommand

  case object DittoMessagesIncoming extends DittoCommand

  final case class AddCart(cartId: CartId, store: Store, replyTo: ActorRef[Validated[Unit]]) extends DittoCommand

  final case class RemoveCart(cartId: CartId, store: Store, replyTo: ActorRef[Validated[Unit]]) extends DittoCommand

  final case class RaiseCartAlarm(cartId: CartId, store: Store) extends DittoCommand

  final case class AssociateCart(cartId: CartId, store: Store, customer: Customer, replyTo: ActorRef[Validated[Unit]])
    extends DittoCommand

  final case class UnlockCart(cartId: CartId, store: Store, replyTo: ActorRef[Validated[Unit]]) extends DittoCommand

  final case class LockCart(cartId: CartId, store: Store, replyTo: ActorRef[Validated[Unit]]) extends DittoCommand

  final case class ItemInsertedIntoCart(cartId: CartId, store: Store, catalogItem: CatalogItem, itemId: ItemId)
    extends DittoCommand

  final case class CartMoved(cartId: CartId, store: Store) extends DittoCommand
}
