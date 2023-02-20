/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes.entities

import spray.json.DefaultJsonProtocol.jsonFormat1
import spray.json.DefaultJsonProtocol.jsonFormat2
import spray.json.DefaultJsonProtocol.jsonFormat3
import spray.json.RootJsonFormat

import carts.cart.valueobjects.{CartId, Customer, Store}
import application.Serializers.given

sealed trait CartEntity

object CartEntity {

  final case class CartAssociationEntity(cartId: CartId, store: Store, customer: Customer) extends CartEntity

  given RootJsonFormat[CartAssociationEntity] = jsonFormat3(CartAssociationEntity.apply)

  final case class CartLockEntity(cartId: CartId, store: Store) extends CartEntity

  given RootJsonFormat[CartLockEntity] = jsonFormat2(CartLockEntity.apply)

  final case class CartUnlockEntity(cartId: CartId, store: Store) extends CartEntity

  given RootJsonFormat[CartUnlockEntity] = jsonFormat2(CartUnlockEntity.apply)

  final case class CartAdditionEntity(store: Store) extends CartEntity

  given RootJsonFormat[CartAdditionEntity] = jsonFormat1(CartAdditionEntity.apply)

  final case class CartRemovalEntity(id: CartId, store: Store) extends CartEntity

  given RootJsonFormat[CartRemovalEntity] = jsonFormat2(CartRemovalEntity.apply)

  final case class StoreCartsShowEntity(store: Store) extends CartEntity

  given RootJsonFormat[StoreCartsShowEntity] = jsonFormat1(StoreCartsShowEntity.apply)
}
