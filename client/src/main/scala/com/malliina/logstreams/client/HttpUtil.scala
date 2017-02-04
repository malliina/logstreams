package com.malliina.logstreams.client

import java.util.Base64

object HttpUtil {
  val Authorization = "Authorization"

  def authorizationValue(username: String, password: String) =
    "Basic " + Base64.getEncoder.encodeToString(s"$username:$password".getBytes("UTF-8"))
}
