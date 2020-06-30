package com.malliina.logstreams.db

import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.malliina.logstreams.db.NewStreamsDatabase.{DatabaseContext, log}
import com.malliina.logstreams.models._
import io.getquill._
import org.flywaydb.core.Flyway
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewStreamsDatabase {
  private val log = Logger(getClass)

  type DatabaseContext =
    MysqlJAsyncContext[CompositeNamingStrategy3[SnakeCase.type, UpperCase.type, MysqlEscape.type]]
      with NewMappings

  private val regex = "jdbc:mysql://([\\.0-9a-zA-Z-]+):?([0-9]*)/([0-9a-zA-Z-]+)".r

  def apply(conf: Conf, ec: ExecutionContext): NewStreamsDatabase = {
    val m = regex.findFirstMatchIn(conf.url).get
    val host = m.group(1)
    val port = m.group(2).toIntOption.getOrElse(3306)
    val name = m.group(3)
    val config = new ConnectionPoolConfiguration(host, port, name, conf.user, conf.pass)
    val pool = MySQLConnectionBuilder.createConnectionPool(config)
    val ctx: DatabaseContext =
      new MysqlJAsyncContext(NamingStrategy(SnakeCase, UpperCase, MysqlEscape), pool)
        with NewMappings
    new NewStreamsDatabase(ctx)(ec)
  }

  def withMigrations(conf: Conf, ec: ExecutionContext): NewStreamsDatabase = {
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
    apply(conf, ec)
  }

  def fail(message: String): Nothing = throw new Exception(message)
}

class NewStreamsDatabase(val ctx: DatabaseContext)(implicit val ec: ExecutionContext)
  extends LogsDatabase {
//  lazy val ctx: MysqlJdbcContext with NewMappings = new MysqlJdbcContext(naming, ds) with NewMappings
  import ctx._

  val logs = quote(querySchema[LogEntryRow]("LOGS"))
  val tokens = quote(querySchema[UserToken]("TOKENS"))

  def insert(events: Seq[LogEntryInput]): Future[EntriesWritten] =
    transactionally(s"Insert ${events.length} events") {
      val insertion = quote {
        liftQuery(events).foreach { e =>
          logs
            .insert(
              _.app -> e.appName,
              _.address -> e.remoteAddress,
              _.timestamp -> e.timestamp,
              _.message -> e.message,
              _.logger -> e.loggerName,
              _.thread -> e.threadName,
              _.level -> e.level,
              _.stacktrace -> e.stackTrace
            )
            .returningGenerated(_.id)
        }
      }
      for {
        inserted <- runIO(insertion)
        rows <- runIO(logs.filter(row => liftQuery(inserted).contains(row.id)))
      } yield EntriesWritten(events, rows)
    }

  def events(query: StreamsQuery = StreamsQuery.default): Future[AppLogEvents] =
    performAsync("Load events") {
      val isAsc = query.order == SortOrder.asc
      val byApps = quote(logs.filter(l => liftQuery(query.apps).contains(l.app)))
      val byAppsAsc = quote {
        byApps
          .sortBy(row => (row.added, row.id))(Ord.asc)
          .drop(lift(query.offset))
          .take(lift(query.limit))
      }
      val byAppsDesc = quote {
        byApps
          .sortBy(row => (row.added, row.id))(Ord.desc)
          .drop(lift(query.offset))
          .take(lift(query.limit))
      }
      val allAsc = quote {
        logs
          .sortBy(row => (row.added, row.id))(Ord.asc)
          .drop(lift(query.offset))
          .take(lift(query.limit))
      }
      val allDesc = quote {
        logs
          .sortBy(row => (row.added, row.id))(Ord.desc)
          .drop(lift(query.offset))
          .take(lift(query.limit))
      }
      val task = if (query.apps.nonEmpty) {
        if (isAsc) runIO(byAppsAsc) else runIO(byAppsDesc)
      } else {
        if (isAsc) runIO(allAsc) else runIO(allDesc)
      }
      task.map { rows => AppLogEvents(rows.map(_.toEvent)) }
    }

  def transactionally[T](name: String)(io: IO[T, _]): Result[T] =
    performAsync(name)(io.transactional)

  def performAsync[T](name: String)(io: IO[T, _]): Result[T] = perform(name, io)

  def perform[T](name: String, io: IO[T, _]): Result[T] = {
    val start = System.currentTimeMillis()
    val result = performIO(io)
    val end = System.currentTimeMillis()
    if (end - start > 500) {
      log.warn(s"$name completed in ${end - start} ms.")
    }
    result
  }

  def first[T, E <: Effect](io: IO[Seq[T], E], onEmpty: => String): IO[T, E] =
    io.flatMap { ts =>
      ts.headOption
        .map { t => IO.successful(t) }
        .getOrElse { IO.failed(new Exception(onEmpty)) }
    }

  def close(): Unit = ctx.close()
}
