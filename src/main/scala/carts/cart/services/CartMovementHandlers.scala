/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.services

import carts.cart.domainevents.CartMoved

trait CartMovementHandlers {

  def onCartMoved(event: CartMoved): Unit
}
