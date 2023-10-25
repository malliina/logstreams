package com.malliina.logstreams.http4s

import com.malliina.logstreams.http4s.Urls.topDomain
import munit.FunSuite

class UrlsTests extends FunSuite:
  test("Urls.topDomain"):
    assertEquals(topDomain(""), "")
    assertEquals(topDomain("aa.b.com"), "b.com")
    assertEquals(topDomain("aaa"), "aaa")
    assertEquals(topDomain("ok.dom.com:443/aaa"), "dom.com")
