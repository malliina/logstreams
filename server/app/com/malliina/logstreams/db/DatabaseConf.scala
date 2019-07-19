package com.malliina.logstreams.db

import java.nio.file.{Files, Path, Paths}
import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.Instant

import com.malliina.values.ErrorMessage
import play.api.{Configuration, Mode}
import slick.ast.FieldSymbol
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile}

object InstantMySQLProfile extends JdbcProfile with MySQLProfile {
  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes {
    override val instantType = new InstantJdbcType {
      override def sqlTypeName(sym: Option[FieldSymbol]) = "TIMESTAMP(3)"
      override def setValue(v: Instant, p: PreparedStatement, idx: Int): Unit =
        p.setTimestamp(idx, Timestamp.from(v))
      override def getValue(r: ResultSet, idx: Int): Instant =
        Option(r.getTimestamp(idx)).map(_.toInstant).orNull
      override def updateValue(v: Instant, r: ResultSet, idx: Int): Unit =
        r.updateTimestamp(idx, Timestamp.from(v))
      override def valueToSQLLiteral(value: Instant): String = s"'${Timestamp.from(value)}'"
    }
  }
}

object InstantH2Profile extends JdbcProfile with H2Profile {
  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes {
    override val instantType = new InstantJdbcType {
      override def sqlTypeName(sym: Option[FieldSymbol]) = "TIMESTAMP(3)"
      override def setValue(v: Instant, p: PreparedStatement, idx: Int): Unit =
        p.setTimestamp(idx, Timestamp.from(v))
      override def getValue(r: ResultSet, idx: Int): Instant =
        Option(r.getTimestamp(idx)).map(_.toInstant).orNull
      override def updateValue(v: Instant, r: ResultSet, idx: Int): Unit =
        r.updateTimestamp(idx, Timestamp.from(v))
      override def valueToSQLLiteral(value: Instant): String = s"'${Timestamp.from(value)}'"
    }
  }
}

case class DatabaseConf(url: String, user: String, pass: String, driver: String, impl: JdbcProfile)

object DatabaseConf {
  val userHome = Paths.get(sys.props("user.home"))
  val HomeKey = "logstreams.home"

  val UrlKey = dbKey("url")
  val UserKey = dbKey("user")
  val PassKey = dbKey("pass")
  val DriverKey = dbKey("driver")

  def dbKey(key: String) = s"logstreams.db.$key"

  val H2Driver = "org.h2.Driver"
  val MariaDriver = "org.mariadb.jdbc.Driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"

  def apply(conf: Configuration, mode: Mode): DatabaseConf =
    if (mode == Mode.Prod) fromConfOrFail(conf)
    else if (mode == Mode.Test) test()
    else localFile()

  def fromEnv(): Either[ErrorMessage, DatabaseConf] =
    build(key => sys.env.get(key).orElse(sys.props.get(key)))

  def fromConfOrFail(conf: Configuration): DatabaseConf =
    build(key => conf.getOptional[String](key)).fold(err => throw new Exception(err.message), identity)

  def fromConf(conf: Configuration): Either[ErrorMessage, DatabaseConf] =
    build(key => conf.getOptional[String](key))

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
      .getOrElse(userHome.resolve(".logstreams"))
    file(homeDir.resolve("db/logsdb"))
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
    case MySQLDriver => Right(InstantMySQLProfile)
    case MariaDriver => Right(InstantMySQLProfile)
    case H2Driver => Right(InstantH2Profile)
    case other => Left(ErrorMessage(s"Unknown driver: '$other'."))
  }
}
