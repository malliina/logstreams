package com.malliina.logstreams.db

case class Conf(url: String, user: String, pass: String, driver: String)

object Conf:
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver

//  def fromConf(conf: Configuration) = fromDatabaseConf(conf.get[Configuration]("logstreams.db"))

//  def fromDatabaseConf(conf: Configuration) = {
//    def read(key: String): Either[String, String] =
//      conf.getOptional[String](key).toRight(s"Key missing: '$key'.")
//
//    for {
//      url <- read("url")
//      user <- read("user")
//      pass <- read("pass")
//    } yield Conf(url, user, pass, read("driver").getOrElse(DefaultDriver))
//  }
