package com.malliina.logstreams.db

case class Conf(url: String, user: String, pass: String, driver: String)

object Conf:
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver
