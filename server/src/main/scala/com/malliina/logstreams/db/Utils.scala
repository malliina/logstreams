package com.malliina.logstreams.db

import com.malliina.logstreams.auth.BasicCredentials
import com.malliina.values.Password
import org.apache.commons.codec.digest.DigestUtils

object Utils {
  def hash(creds: BasicCredentials): Password =
    Password(DigestUtils.md5Hex(creds.username.name + ":" + creds.password.pass))
}
