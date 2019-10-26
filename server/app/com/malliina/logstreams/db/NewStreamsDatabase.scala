package com.malliina.logstreams.db

import akka.actor.ActorSystem
import com.malliina.logstreams.db.NewStreamsDatabase.log
import com.malliina.logstreams.models._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill._
import org.flywaydb.core.Flyway
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewStreamsDatabase {
  private val log = Logger(getClass)

  def withMigrations(as: ActorSystem, conf: Conf) = {
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
    apply(as, conf)
  }

  private def apply(as: ActorSystem, dbConf: Conf): NewStreamsDatabase = {
    val pool = as.dispatchers.lookup("contexts.database")
    apply(dataSource(dbConf), pool)
  }

  private def apply(ds: HikariDataSource, ec: ExecutionContext): NewStreamsDatabase =
    new NewStreamsDatabase(ds)(ec)

  def dataSource(conf: Conf): HikariDataSource = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName(Conf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    log info s"Connecting to '${conf.url}'..."
    new HikariDataSource(hikari)
  }

  def fail(message: String): Nothing = throw new Exception(message)
}

class NewStreamsDatabase(val ds: HikariDataSource)(implicit val ec: ExecutionContext)
    extends LogsDatabase {
  val naming = NamingStrategy(SnakeCase, UpperCase, MysqlEscape)
  lazy val ctx = new MysqlJdbcContext(naming, ds) with NewMappings
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
      task.map { rows =>
        AppLogEvents(rows.map(_.toEvent))
      }
    }

  def transactionally[T](name: String)(io: IO[T, _]): Future[Result[T]] =
    performAsync(name)(io.transactional)

  def performAsync[T](name: String)(io: IO[T, _]): Future[Result[T]] = Future(perform(name, io))(ec)

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
      ts.headOption.map { t =>
        IO.successful(t)
      }.getOrElse { IO.failed(new Exception(onEmpty)) }
    }

  def close(): Unit = ds.close()
}
