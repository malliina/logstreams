package com.malliina.logstreams.http4s

import com.malliina.values.Username
import org.http4s.Uri
import org.http4s.implicits._

object LogRoutes extends LogRoutes

trait LogRoutes {
  val index = uri"/"
  val googleStart = uri"/oauth"
  val googleCallback = uri"/oauthcb"
  val sources = uri"/sources"
  val allUsers = uri"/users"
  val addUser = uri"/users"

  def removeUser(user: Username) = Uri.unsafeFromString(s"/users/$user/delete")
}
