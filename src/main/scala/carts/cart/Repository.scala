/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart

import carts.cart.valueobjects.{CartId, Store}
import carts.Validated
import carts.cart.entities.Cart

trait Repository {

  def findById(cartId: CartId, store: Store): Validated[Cart]

  def findByStore(store: Store): Validated[Set[Cart]]

  def add(cart: Cart): Validated[Unit]

  def update(cart: Cart): Validated[Unit]

  def remove(cart: Cart): Validated[Unit]
}
