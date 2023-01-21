/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.entities

import carts.cart.valueobjects.{CartId, Store}

trait Cart {

  val cartId: CartId

  val store: Store

  val isMovable: Boolean
}
