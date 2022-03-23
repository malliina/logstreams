package com.malliina.logstreams.db

import cats.effect.IO.*
import cats.effect.kernel.Resource
import cats.effect.IO
import com.malliina.logstreams.db.DoobieDatabase.log
import com.malliina.util.AppLogger
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.*
import doobie.implicits.*
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

import scala.concurrent.duration.DurationInt

object DoobieDatabase:
  private val log = AppLogger(getClass)

  def default(conf: Conf): Resource[IO, DoobieDatabase] =
    for
      ds <- dataSource(conf)
      tx <- transactor(ds)
    yield DoobieDatabase(tx)

  def withMigrations(conf: Conf): Resource[IO, DoobieDatabase] =
    Resource.eval(migrate(conf)).flatMap { _ => default(conf) }

  private def migrate(conf: Conf): IO[MigrateResult] = IO {
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
  }

  private def dataSource(conf: Conf): Resource[IO, HikariDataSource] =
    val hikari = new HikariConfig()
    hikari.setDriverClassName(Conf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    hikari.setMaxLifetime(60.seconds.toMillis)
    hikari.setMaximumPoolSize(5)
    Resource.make(IO {
      log.info(s"Connecting to '${conf.url}'...")
      HikariDataSource(hikari)
    })(ds => IO(ds.close()))

  def transactor(ds: HikariDataSource): Resource[IO, DataSourceTransactor[IO]] =
    for ec <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
    yield Transactor.fromDataSource[IO](ds, ec)

class DoobieDatabase(tx: DataSourceTransactor[IO]):
  implicit val logHandler: LogHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      val logger: String => Unit = if processing > 2.seconds then log.info else log.debug
      logger(s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms.")
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }

  def run[T](io: ConnectionIO[T]): IO[T] = io.transact(tx)
