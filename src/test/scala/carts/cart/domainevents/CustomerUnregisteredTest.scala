/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.domainevents

import io.github.pervasivecats.carts.cart.valueobjects.Customer

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

class CustomerUnregisteredTest extends AnyFunSpec {

  describe("A customer unregistered event") {
    describe("when created with a customer") {
      it("should contain it") {
        val customer: Customer = Customer("addr_3ss!@email.com").getOrElse(fail())

        CustomerUnregistered(customer).customer shouldBe customer
      }
    }
  }
}
