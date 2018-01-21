package com.malliina.logstreams.db

import java.io.Closeable
import java.nio.file.{Files, Path, Paths}
import javax.sql.DataSource

import com.malliina.file.{FileUtilities, StorageFile}
import com.malliina.play.models.{Password, Username}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import play.api.Logger
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile}

class UserDB(ds: DataSource, override val impl: JdbcProfile)
  extends DatabaseLike(impl, impl.api.Database.forDataSource(ds, None))
    with Closeable {

  import impl.api._

  object mappings extends Mappings(impl)

  import mappings._

  val users = TableQuery[Users]
  val tokens = TableQuery[Tokens]

  override val tableQueries = Seq(tokens, users)

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

  override def close(): Unit = {
    database.close()
  }
}

object UserDB {
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

  def init(allowFallback: Boolean): UserDB = {
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

  def h2(conn: String): UserDB = apply(DatabaseConf.h2(conn))

  def apply(conf: DatabaseConf): UserDB = {
    val hikariConf = new HikariConfig()
    hikariConf.setJdbcUrl(conf.url)
    hikariConf.setDriverClassName(conf.driver)
    hikariConf.setUsername(conf.user)
    hikariConf.setPassword(conf.pass)
    log.info(s"Connecting to '${conf.url}'...")
    val ds = new HikariDataSource(hikariConf)
    new UserDB(ds, conf.impl)
  }

  def default(): UserDB = {
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
