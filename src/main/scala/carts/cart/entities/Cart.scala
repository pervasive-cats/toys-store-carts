/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.entities

import AnyOps.===
import carts.cart.valueobjects.{CartId, Store}

trait Cart {

  val cartId: CartId

  val store: Store

  val movable: Boolean
}

object Cart {

  def cartsEquals(obj: Any)(cartId: CartId, store: Store): Boolean = obj match {
    case cart: Cart => cart.cartId.value === cartId.value && cart.store.value === store.value
    case _ => false
  }
}
