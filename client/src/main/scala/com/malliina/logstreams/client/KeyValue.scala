package com.malliina.logstreams.client

import scala.language.implicitConversions

case class KeyValue(key: String, value: String)

object KeyValue:
  given Conversion[(String, String), KeyValue] = t => KeyValue(t._1, t._2)
