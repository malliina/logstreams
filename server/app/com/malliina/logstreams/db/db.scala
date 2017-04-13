package com.malliina.logstreams.db

import java.io.Closeable
import java.nio.file.{Files, Path, Paths}

import com.malliina.file.{FileUtilities, StorageFile}
import com.malliina.logstreams.db.Mappings.{password, username}
import com.malliina.logstreams.db.UserDB.log
import com.malliina.play.models.{Password, Username}
import org.h2.jdbcx.JdbcConnectionPool
import play.api.Logger
import slick.jdbc.H2Profile.api._

class Users(tag: Tag) extends Table[DataUser](tag, "USERS") {
  def user = column[Username]("USER", O.PrimaryKey)

  def passHash = column[Password]("PASS_HASH")

  def * = (user, passHash) <> ((DataUser.apply _).tupled, DataUser.unapply)
}

class Tokens(tag: Tag) extends Table[DataUser](tag, "TOKENS") {
  def tokenUser = column[Username]("TOKEN_USER", O.PrimaryKey)

  def token = column[Password]("TOKEN")

  def user = column[Username]("USER")

  def userConstraint = foreignKey("FK_TOKEN_USER", user, UserDB.users)(
    _.user,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade)

  def * = (user, token) <> ((DataUser.apply _).tupled, DataUser.unapply)
}

class UserDB(conn: String) extends DatabaseLike with Closeable {
  val url = s"jdbc:h2:$conn;DB_CLOSE_DELAY=-1"
  log info s"Connecting to: $url"
  val pool = JdbcConnectionPool.create(url, "", "")
  override val database = Database.forDataSource(pool, None)
  override val tableQueries = Seq(UserDB.tokens, UserDB.users)

  init()

  override def close() = {
    database.close()
    pool.dispose()
  }
}

object UserDB {
  private val log = Logger(getClass)

  val users = TableQuery[Users]
  val tokens = TableQuery[Tokens]
  val HomeKey = "logstreams.home"

  def default() = {
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
    new UserDB(path.toString)
  }

  /**
    * @return an in-memory database
    */
  def test() = new UserDB("mem:test")
}
