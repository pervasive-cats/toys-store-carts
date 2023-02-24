/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart.valueobjects

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import carts.cart.valueobjects.Customer.WrongCustomerFormat

class CustomerTest extends AnyFunSpec {

  describe("A customer's email") {
    describe("when created with an initial dot") {
      it("should not be valid") {
        Customer(".addr_3ss!@email.com").left.value shouldBe WrongCustomerFormat
      }
    }

    describe("when created with an uppercase local part") {
      it("should not be valid") {
        Customer("Addr_3ss!@email.com").left.value shouldBe WrongCustomerFormat
      }
    }

    describe("when created without an at-sign") {
      it("should not be valid") {
        Customer("addr_3ss!email.com").left.value shouldBe WrongCustomerFormat
      }
    }

    describe("when created without a domain") {
      it("should not be valid") {
        Customer("addr_3ss!@.com").left.value shouldBe WrongCustomerFormat
      }
    }

    describe("when created without a top level domain") {
      it("should not be valid") {
        Customer("addr_3ss!@email").left.value shouldBe WrongCustomerFormat
      }
    }

    describe("when created with an illegal domain") {
      it("should not be valid") {
        Customer("addr_3ss!@EM_ail.com").left.value shouldBe WrongCustomerFormat
      }
    }

    describe("when created with an illegal top level domain") {
      it("should not be valid") {
        Customer("addr_3ss!@email.C_O_M").left.value shouldBe WrongCustomerFormat
      }
    }

    describe("when created following the correct format") {
      it("should be valid") {
        val email: String = "addr_3ss!@email.com"

        (Customer(email).value.value: String) shouldBe email
      }
    }

    describe("when created using multiple dots, but following the correct format") {
      it("should be valid") {
        val email: String = "addr.3ss.!@email.test.com"

        (Customer(email).value.value: String) shouldBe email
      }
    }
  }
}
