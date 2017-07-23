package com.malliina.logstreams.db

import java.io.Closeable
import java.nio.file.{Files, Path, Paths}
import javax.sql.DataSource

import com.malliina.file.{FileUtilities, StorageFile}
import com.malliina.logstreams.db.Mappings.{password, username}
import com.malliina.logstreams.db.UserDB.log
import com.malliina.play.models.{Password, Username}
import org.h2.jdbcx.JdbcConnectionPool
import play.api.Logger
import slick.jdbc.H2Profile.api._

import scala.concurrent.ExecutionContext

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

class UserDB(ds: JdbcConnectionPool)(implicit val ec: ExecutionContext)
  extends DatabaseLike(Database.forDataSource(ds, None))
    with Closeable {

  override val tableQueries = Seq(UserDB.tokens, UserDB.users)

  init()

  override def close(): Unit = {
    database.close()
    ds.dispose()
  }
}

object UserDB {
  private val log = Logger(getClass)

  val users = TableQuery[Users]
  val tokens = TableQuery[Tokens]
  val HomeKey = "logstreams.home"

  def forConn(conn: String)(implicit ec: ExecutionContext): UserDB = {
    val url = s"jdbc:h2:$conn;DB_CLOSE_DELAY=-1"
    log info s"Connecting to: $url"
    val pool = JdbcConnectionPool.create(url, "", "")
    new UserDB(pool)
  }

  def default()(implicit ec: ExecutionContext) = {
    val homeDir = (sys.props.get(HomeKey) orElse sys.env.get(HomeKey)).map(p => Paths.get(p))
      .getOrElse(FileUtilities.userHome / ".logstreams")
    file(homeDir / "db" / "logsdb")
  }

  /**
    * @param path path to database file
    * @return a file-based database stored at `path`
    */
  def file(path: Path)(implicit ec: ExecutionContext) = {
    Option(path.getParent).foreach(p => Files.createDirectories(p))
    forConn(path.toString)
  }

  /**
    * @return an in-memory database
    */
  def test()(implicit ec: ExecutionContext) = forConn("mem:test")
}
