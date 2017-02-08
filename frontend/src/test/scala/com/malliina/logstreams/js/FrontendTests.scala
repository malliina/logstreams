package com.malliina.logstreams.js

import upickle.Js
import utest._

object FrontendTests extends TestSuite {
  override def tests = TestSuite {
    'CanRunTest {
      assert(1 == 2 - 1)
    }

    'LongToLong {
      val n = Js.Num(1486579409148L)
      val parsed = PimpJSON.readJs[Long](n)
      assert(parsed == 1486579409148L)
    }
  }
}
