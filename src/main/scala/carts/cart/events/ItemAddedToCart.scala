/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.events

import carts.cart.valueobjects.{CartId, Store}

trait ItemAddedToCart {

  val cartId: CartId

  val store: Store
}
