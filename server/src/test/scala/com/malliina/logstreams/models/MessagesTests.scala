package com.malliina.logstreams.models

import java.time.Instant

import munit.FunSuite

class MessagesTests extends FunSuite {
  test("time formatting") {
    val f = LogEntryRow.format(Instant.now())
    assert(f.length > 10)
    assert(f.contains(" "))
  }
}
