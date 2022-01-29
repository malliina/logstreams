package com.malliina.logstreams.http4s

import com.malliina.values.Username
import org.http4s.*
import org.http4s.implicits.*

object LogRoutes extends LogRoutes

trait LogRoutes:
  val index = uri"/"
  val googleStart = uri"/oauth"
  val googleCallback = uri"/oauthcb"
  val sources = uri"/sources"
  val allUsers = uri"/users"
  val addUser = uri"/users"
  object sockets:
    val admins = uri"/ws/admins"
    val logs = uri"/ws/logs"
    val sources = uri"/ws/sources"

  def removeUser(user: Username) = Uri.unsafeFromString(s"/users/$user/delete")
