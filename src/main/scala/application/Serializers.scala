/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application

import eu.timepit.refined.auto.given
import spray.json.DefaultJsonProtocol
import spray.json.JsBoolean
import spray.json.JsNull
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.deserializationError
import spray.json.enrichAny

import carts.cart.domainevents.*
import carts.cart.entities.*
import carts.cart.valueobjects.*
import carts.cart.valueobjects.item.*

object Serializers extends DefaultJsonProtocol {

  private def stringSerializer[A](extractor: A => String, builder: String => Validated[A]): JsonFormat[A] = new JsonFormat[A] {

    override def read(json: JsValue): A = json match {
      case JsString(value) => builder(value).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(obj: A): JsValue = extractor(obj).toJson
  }

  given JsonFormat[Customer] = stringSerializer(_.value, Customer.apply)

  private def longSerializer[A](extractor: A => Long, builder: Long => Validated[A]): JsonFormat[A] = new JsonFormat[A] {

    override def read(json: JsValue): A = json match {
      case JsNumber(value) if value.isValidLong =>
        builder(value.longValue).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(obj: A): JsValue = extractor(obj).toJson
  }

  given JsonFormat[CartId] = longSerializer(_.value, CartId.apply)

  given JsonFormat[Store] = longSerializer(_.value, Store.apply)

  given JsonFormat[ItemId] = longSerializer(_.value, ItemId.apply)

  given JsonFormat[CatalogItem] = longSerializer(_.value, CatalogItem.apply)

  given JsonFormat[Item] with {

    override def read(json: JsValue): Item = json.asJsObject.getFields("id", "kind", "store") match {
      case Seq(JsNumber(id), JsNumber(kind), JsNumber(store)) if id.isValidLong && kind.isValidLong && store.isValidLong =>
        (for {
          i <- ItemId(id.longValue)
          k <- CatalogItem(kind.longValue)
          s <- Store(store.longValue)
        } yield Item(k, s, i)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(item: Item): JsValue = JsObject(
      "id" -> item.itemId.toJson,
      "kind" -> item.catalogItem.toJson,
      "store" -> item.store.toJson
    )
  }

  given JsonFormat[Cart] with {

    override def read(json: JsValue): Cart = json.asJsObject.getFields("id", "store", "movable", "customer") match {
      case Seq(JsNumber(id), JsNumber(store), JsBoolean(_), JsString(customer)) if id.isValidLong && store.isValidLong =>
        (for {
          i <- CartId(id.longValue)
          s <- Store(store.longValue)
          c <- Customer(customer)
        } yield AssociatedCart(i, s, c)).fold(e => deserializationError(e.message), identity)
      case Seq(JsNumber(id), JsNumber(store), JsBoolean(movable), JsNull) if id.isValidLong && store.isValidLong =>
        (for {
          i <- CartId(id.longValue)
          s <- Store(store.longValue)
        } yield if (movable) UnlockedCart(i, s) else LockedCart(i, s)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(cart: Cart): JsValue = JsObject(
      "id" -> cart.cartId.toJson,
      "store" -> cart.store.toJson,
      "movable" -> (cart match {
        case _: LockedCart => false
        case _ => true
      }).toJson,
      "customer" -> (cart match {
        case cart: AssociatedCart => cart.customer.toJson
        case _ => JsNull
      })
    )
  }

  given JsonFormat[CartMoved] with {

    override def read(json: JsValue): CartMoved = json.asJsObject.getFields("id", "store") match {
      case Seq(JsNumber(id), JsNumber(store)) if id.isValidLong && store.isValidLong =>
        (for {
          i <- CartId(id.longValue)
          s <- Store(store.longValue)
        } yield CartMoved(i, s)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(cartMoved: CartMoved): JsValue = JsObject(
      "type" -> "CartMoved".toJson,
      "id" -> cartMoved.cartId.toJson,
      "store" -> cartMoved.store.toJson
    )
  }

  given JsonFormat[ItemAddedToCart] with {

    override def read(json: JsValue): ItemAddedToCart = json.asJsObject.getFields("id", "kind", "store", "customer") match {
      case Seq(JsNumber(id), JsNumber(kind), JsNumber(store), JsString(customer))
           if id.isValidLong && kind.isValidLong &&
           store.isValidLong =>
        (for {
          i <- ItemId(id.longValue)
          k <- CatalogItem(kind.longValue)
          s <- Store(store.longValue)
          c <- Customer(customer)
        } yield ItemAddedToCart(c, s, k, i)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(itemAddedToCart: ItemAddedToCart): JsValue = JsObject(
      "id" -> itemAddedToCart.itemId.toJson,
      "kind" -> itemAddedToCart.catalogItem.toJson,
      "store" -> itemAddedToCart.store.toJson,
      "customer" -> itemAddedToCart.customer.toJson
    )
  }

  given JsonFormat[ItemInsertedIntoCart] with {

    override def read(json: JsValue): ItemInsertedIntoCart = json.asJsObject.getFields("id", "kind", "store", "cartId") match {
      case Seq(JsNumber(id), JsNumber(kind), JsNumber(store), JsNumber(cartId))
           if id.isValidLong && kind.isValidLong &&
           store.isValidLong && cartId.isValidLong =>
        (for {
          i <- ItemId(id.longValue)
          k <- CatalogItem(kind.longValue)
          s <- Store(store.longValue)
          c <- CartId(cartId.longValue)
        } yield ItemInsertedIntoCart(c, s, k, i)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(itemInsertedIntoCart: ItemInsertedIntoCart): JsValue = JsObject(
      "id" -> itemInsertedIntoCart.itemId.toJson,
      "kind" -> itemInsertedIntoCart.catalogItem.toJson,
      "store" -> itemInsertedIntoCart.store.toJson,
      "cartId" -> itemInsertedIntoCart.cartId.toJson
    )
  }

  given JsonFormat[CartAssociated] with {

    override def read(json: JsValue): CartAssociated = json.asJsObject.getFields("id", "store", "customer") match {
      case Seq(JsNumber(id), JsNumber(store), JsString(customer)) if id.isValidLong && store.isValidLong =>
        (for {
          i <- CartId(id.longValue)
          s <- Store(store.longValue)
          c <- Customer(customer)
        } yield CartAssociated(i, s, c)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(cartAssociated: CartAssociated): JsValue = JsObject(
      "id" -> cartAssociated.cartId.toJson,
      "store" -> cartAssociated.store.toJson,
      "customer" -> cartAssociated.customer.toJson
    )
  }
}
