package com.malliina.logstreams.db

case class Conf(
  url: String,
  user: String,
  pass: String,
  driver: String,
  maxPoolSize: Int,
  autoMigrate: Boolean
)

object Conf:
  val MySQLDriver = "com.mysql.cj.jdbc.Driver"
