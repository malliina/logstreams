package com.malliina.logstreams.client

import java.nio.charset.StandardCharsets
import java.util.Base64

object HttpUtil:
  val Authorization = "Authorization"
  val Basic = "Basic"

  def basicAuth(username: String, password: String): KeyValue =
    KeyValue(Authorization, authorizationValue(username, password))

  def authorizationValue(username: String, password: String) =
    val bytes = s"$username:$password".getBytes(StandardCharsets.UTF_8)
    val bytesStringified = Base64.getEncoder.encodeToString(bytes)
    s"$Basic $bytesStringified"
