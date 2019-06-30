package com.malliina.logstreams.db

import java.io.Closeable
import java.time.Instant

import ch.qos.logback.classic.Level
import com.malliina.logstreams.db.StreamsSchema.NumThreads
import com.malliina.logstreams.models.{LogEntryId, LogEntryInput, LogEntryRow}
import com.malliina.values.{Password, Username}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import play.api.Logger
import slick.jdbc.JdbcProfile
import slick.util.AsyncExecutor

object StreamsSchema {
  private val log = Logger(getClass)

  val NumThreads = 40

  def apply(conf: DatabaseConf): StreamsSchema = {
    val hikariConf = new HikariConfig()
    hikariConf.setJdbcUrl(conf.url)
    hikariConf.setDriverClassName(conf.driver)
    hikariConf.setUsername(conf.user)
    hikariConf.setPassword(conf.pass)
    log.info(s"Connecting to '${conf.url}'...")
    val ds = new HikariDataSource(hikariConf)
    new StreamsSchema(ds, conf.impl)
  }

  // https://github.com/slick/slick/issues/1614#issuecomment-284730145
  def executor(threads: Int = NumThreads): AsyncExecutor = AsyncExecutor(
    name = "AsyncExecutor.boat",
    minThreads = threads,
    maxThreads = threads,
    queueSize = 20000,
    maxConnections = threads
  )
}

class StreamsSchema(ds: DataSource, override val impl: JdbcProfile)
  extends DatabaseLike(impl, impl.api.Database.forDataSource(ds, Option(NumThreads), executor = StreamsSchema.executor(NumThreads)))
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
    def timestamp = column[Instant]("TIMESTAMP", O.SqlType("TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL"))
    def message = column[String]("MESSAGE")
    def loggerName = column[String]("LOGGER")
    def threadName = column[String]("THREAD")
    def level = column[Level]("LEVEL")
    def stackTrace = column[Option[String]]("STACKTRACE")
    // The clauses DEFAULT CURRENT_TIMESTAMP and ON UPDATE CURRENT_TIMESTAMP are by default applied to a timestamp
    // field, and enable the default behavior.
    def added = column[Instant]("ADDED", O.SqlType("TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3)"))

    def userConstraint = foreignKey("FK_LOG_USER", app, users)(
      _.user,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction)

    def forInsert = (app, remoteAddress, timestamp, message, loggerName, threadName, level, stackTrace) <> ((LogEntryInput.apply _).tupled, LogEntryInput.unapply)

    def * = (id, app, remoteAddress, timestamp, message, loggerName, threadName, level, stackTrace, added) <> ((LogEntryRow.apply _).tupled, LogEntryRow.unapply)
  }

  override def close(): Unit = database.close()
}
