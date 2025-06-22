package com.malliina.logstreams.http4s

import cats.data.NonEmptyList
import com.malliina.logstreams.db.StreamsQuery
import com.malliina.values.Username
import org.http4s.Uri
import org.http4s.implicits.uri

object LogRoutes extends LogRoutes

trait LogRoutes:
  val index = uri"/"
  val logs = uri"/logs"
  val googleStart = uri"/oauth"
  val googleCallback = uri"/oauthcb"
  val sources = uri"/sources"
  val allUsers = uri"/users"
  val addUser = uri"/users"

  object sockets:
    val admins = uri"/ws/admins"
    val logs = uri"/ws/logs"
    val sources = uri"/ws/sources"

  def removeUser(user: Username) = uri"/users" / user.name / "delete"

//  private def toLogs(query: StreamsQuery): Uri =
//    toLogs(StreamsQuery.toQuery(query))

  def toLogs(qs: Map[String, NonEmptyList[String]]): Uri =
    logs.withMultiValueQueryParams(qs.map((k, v) => k -> v.toList))
