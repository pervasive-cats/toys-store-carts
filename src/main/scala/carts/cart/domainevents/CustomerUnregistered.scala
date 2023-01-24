/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.domainevents

import carts.cart.valueobjects.Customer

trait CustomerUnregistered {

  val customer: Customer
}

object CustomerUnregistered {

  private case class CustomerUnregisteredImpl(customer: Customer) extends CustomerUnregistered

  def apply(customer: Customer): CustomerUnregistered = CustomerUnregisteredImpl(customer)
}
