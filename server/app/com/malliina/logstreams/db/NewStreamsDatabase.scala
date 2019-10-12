package com.malliina.logstreams.db

import java.time.Instant

import akka.actor.ActorSystem
import com.malliina.logstreams.db.NewMappings._
import com.malliina.logstreams.models._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill._
import org.flywaydb.core.Flyway
import play.api.Logger
import scala.concurrent.{ExecutionContext, Future}

object NewStreamsDatabase {
  private val log = Logger(getClass)

  def apply(as: ActorSystem, dbConf: Conf): NewStreamsDatabase = {
    val pool = as.dispatchers.lookup("contexts.database")
    apply(dataSource(dbConf), pool)
  }

  def apply(ds: HikariDataSource, ec: ExecutionContext): NewStreamsDatabase =
    new NewStreamsDatabase(ds)(ec)

  def mysqlFromEnvOrFail(as: ActorSystem) = withMigrations(as, Conf.fromEnvOrFail())

  def withMigrations(as: ActorSystem, conf: Conf) = {
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
    apply(as, conf)
  }

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
  lazy val ctx = new MysqlJdbcContext(naming, ds)
  import ctx._

  val logs = quote(querySchema[LogEntryRow]("LOGS"))
  val tokens = quote(querySchema[UserToken]("TOKENS"))

  def insert(events: Seq[LogEntryInput]): Future[EntriesWritten] = Future {
    transaction {
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
      val inserted = run(insertion)
      val rows = quote {
        logs.filter(row => liftQuery(inserted).contains(row.id))
      }
      EntriesWritten(events, if (inserted.isEmpty) Nil else run(rows))
    }
  }

  def events(query: StreamsQuery = StreamsQuery.default): Future[AppLogEvents] = Future {
    val isAsc = query.order == SortOrder.asc
    val base =
      if (query.apps.nonEmpty) quote(logs.filter(l => liftQuery(query.apps).contains(l.app)))
      else quote(logs)
    val ord =
      if (isAsc) quote(Ord.asc[(Instant, LogEntryId)]) else quote(Ord.desc[(Instant, LogEntryId)])
    val q = quote {
      val sorted = base.sortBy(row => (row.added, row.id))(ord)
      sorted
        .drop(lift(query.offset))
        .take(lift(query.limit))
    }
    AppLogEvents(run(q).map(_.toEvent))
  }

  def close(): Unit = ds.close()
}
