/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 *
 */

package io.github.pervasivecats

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

class Test extends AnyFunSpec {

  describe("A Set") {
    describe("when empty") {
      it("should have size 0") {
        Set.empty[Int].size shouldBe 0
      }
    }
  }
}
