/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.services

case object EventRejected extends ValidationError {

  override val message: String = "The event could not be properly handled"
}
