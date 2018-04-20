package com.malliina.logstreams.db

import java.nio.file.{Files, Path, Paths}

import com.malliina.file.{FileUtilities, StorageFile}
import com.malliina.values.ErrorMessage
import play.api.{Configuration, Mode}
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile}

case class DatabaseConf(url: String, user: String, pass: String, driver: String, impl: JdbcProfile)

object DatabaseConf {
  val HomeKey = "logstreams.home"

  val UrlKey = "db_url"
  val UserKey = "db_user"
  val PassKey = "db_pass"
  val DriverKey = "db_driver"

  val H2Driver = "org.h2.Driver"
  val MariaDriver = "org.mariadb.jdbc.Driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"

  def apply(conf: Configuration, mode: Mode): DatabaseConf =
    if (mode == Mode.Prod) fromConfOrFail(conf)
    else if (mode == Mode.Test) test()
    else localFile()

  def fromEnv(): Either[ErrorMessage, DatabaseConf] = {
    build(key => sys.env.get(key).orElse(sys.props.get(key)))
  }

  def fromConfOrFail(conf: Configuration): DatabaseConf = {
    build(key => conf.getOptional[String](key)).fold(err => throw new Exception(err.message), identity)
  }

  def fromConf(conf: Configuration): Either[ErrorMessage, DatabaseConf] = {
    build(key => conf.getOptional[String](key))
  }

  def build(readKey: String => Option[String]) = {
    def read(key: String) = readKey(key).toRight(ErrorMessage(s"Key missing: '$key'."))

    for {
      url <- read(UrlKey)
      user <- read(UserKey)
      pass <- read(PassKey)
      driver <- read(DriverKey)
      impl <- impl(driver)
    } yield {
      DatabaseConf(url, user, pass, driver, impl)
    }
  }

  def localFile(): DatabaseConf = {
    val homeDir = (sys.props.get(HomeKey) orElse sys.env.get(HomeKey)).map(p => Paths.get(p))
      .getOrElse(FileUtilities.userHome / ".logstreams")
    file(homeDir / "db" / "logsdb")
  }

  /**
    * @param path path to database file
    * @return a file-based database stored at `path`
    */
  def file(path: Path) = {
    Option(path.getParent).foreach(p => Files.createDirectories(p))
    h2(path.toString)
  }

  /**
    * @return an in-memory database
    */
  def test() = inMemory()

  def inMemory() = h2("mem:test")

  def h2(conn: String) = DatabaseConf(s"jdbc:h2:$conn;DB_CLOSE_DELAY=-1", "", "", H2Driver, H2Profile)

  def impl(brand: String): Either[ErrorMessage, JdbcProfile] = brand match {
    case MySQLDriver => Right(MySQLProfile)
    case MariaDriver => Right(MySQLProfile)
    case H2Driver => Right(H2Profile)
    case other => Left(ErrorMessage(s"Unknown driver: '$other'."))
  }
}
