package com.malliina.logstreams.db

import java.io.Closeable
import java.nio.file.{Files, Path, Paths}
import java.time.Instant

import ch.qos.logback.classic.Level
import com.malliina.file.{FileUtilities, StorageFile}
import com.malliina.logstreams.models.{LogEntryId, LogEntryInput, LogEntryRow}
import com.malliina.play.models.{Password, Username}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import play.api.Logger
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile}

class StreamsDB(ds: DataSource, override val impl: JdbcProfile)
  extends DatabaseLike(impl, impl.api.Database.forDataSource(ds, None))
    with Closeable {

  import impl.api._

  object mappings extends Mappings(impl)

  import mappings._

  val users = TableQuery[Users]
  val tokens = TableQuery[Tokens]
  val logEntries = TableQuery[LogEntries]

  override val tableQueries = Seq(logEntries, tokens, users)

  init()

  class Users(tag: Tag) extends Table[DataUser](tag, "USERS") {
    def user = column[Username]("USER", O.PrimaryKey, O.Length(100))

    def passHash = column[Password]("PASS_HASH", O.Length(254))

    def * = (user, passHash) <> ((DataUser.apply _).tupled, DataUser.unapply)
  }

  class Tokens(tag: Tag) extends Table[DataUser](tag, "TOKENS") {
    def tokenUser = column[Username]("TOKEN_USER", O.PrimaryKey, O.Length(100))

    def token = column[Password]("TOKEN")

    def user = column[Username]("USER", O.Length(100))

    def userConstraint = foreignKey("FK_TOKEN_USER", user, users)(
      _.user,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade)

    def * = (user, token) <> ((DataUser.apply _).tupled, DataUser.unapply)
  }

  class LogEntries(tag: Tag) extends Table[LogEntryRow](tag, "LOGS") {
    def id = column[LogEntryId]("ID", O.PrimaryKey, O.AutoInc)

    def app = column[Username]("APP", O.Length(100))

    def remoteAddress = column[String]("ADDRESS")

    def timestamp = column[Instant]("TIMESTAMP")

    def message = column[String]("MESSAGE")

    def loggerName = column[String]("LOGGER")

    def threadName = column[String]("THREAD")

    def level = column[Level]("LEVEL")

    def stackTrace = column[Option[String]]("STACKTRACE")

    // The clauses DEFAULT CURRENT_TIMESTAMP and ON UPDATE CURRENT_TIMESTAMP are by default applied to a timestamp
    // field, and enable the default behavior.
    def added = column[Instant]("ADDED", O.SqlType("TIMESTAMP DEFAULT CURRENT_TIMESTAMP"))

    def userConstraint = foreignKey("FK_LOG_USER", app, users)(
      _.user,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction)

    def forInsert = (app, remoteAddress, timestamp, message, loggerName, threadName, level, stackTrace) <> ((LogEntryInput.apply _).tupled, LogEntryInput.unapply)

    def * = (id, app, remoteAddress, timestamp, message, loggerName, threadName, level, stackTrace, added) <> ((LogEntryRow.apply _).tupled, LogEntryRow.unapply)
  }

  override def close(): Unit = {
    database.close()
  }
}

object StreamsDB {
  private val log = Logger(getClass)

  val HomeKey = "logstreams.home"
  val UrlKey = "db_url"
  val UserKey = "db_user"
  val PassKey = "db_pass"
  val DriverKey = "db_driver"

  val H2Driver = "org.h2.Driver"
  val MariaDriver = "org.mariadb.jdbc.Driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"

  case class DatabaseConf(url: String, user: String, pass: String, driver: String, impl: JdbcProfile)

  object DatabaseConf {
    def fromEnv(): Either[String, DatabaseConf] = {
      def read(key: String) = sys.env.get(key).orElse(sys.props.get(key)).toRight(s"Key missing: '$key'.")

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

    def h2(conn: String) = DatabaseConf(s"jdbc:h2:$conn;DB_CLOSE_DELAY=-1", "", "", H2Driver, H2Profile)
  }

  def init(allowFallback: Boolean): StreamsDB = {
    DatabaseConf.fromEnv().map(apply).fold(
      err =>
        if (allowFallback) {
          log.info(s"$err Falling back to file-based database.")
          default()
        } else {
          throw new Exception(err)
        },
      identity
    )
  }

  def h2(conn: String): StreamsDB = apply(DatabaseConf.h2(conn))

  def apply(conf: DatabaseConf): StreamsDB = {
    val hikariConf = new HikariConfig()
    hikariConf.setJdbcUrl(conf.url)
    hikariConf.setDriverClassName(conf.driver)
    hikariConf.setUsername(conf.user)
    hikariConf.setPassword(conf.pass)
    log.info(s"Connecting to '${conf.url}'...")
    val ds = new HikariDataSource(hikariConf)
    new StreamsDB(ds, conf.impl)
  }

  def default(): StreamsDB = {
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

  def impl(brand: String): Either[String, JdbcProfile] = brand match {
    case MySQLDriver => Right(MySQLProfile)
    case MariaDriver => Right(MySQLProfile)
    case H2Driver => Right(H2Profile)
    case other => Left(s"Unknown driver: '$other'.")
  }
}
