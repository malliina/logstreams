package com.malliina.logstreams.db

import cats.Monad
import cats.effect.IO.*
import cats.effect.kernel.Resource
import cats.effect.{Async, IO, Sync}
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

  def init[F[_]: Async](conf: Conf): Resource[F, DoobieDatabase[F]] =
    if conf.autoMigrate then withMigrations(conf) else default(conf)

  def default[F[_]: Async](conf: Conf): Resource[F, DoobieDatabase[F]] =
    for
      ds <- dataSource(conf)
      tx <- transactor(ds)
    yield DoobieDatabase(tx)

  private def withMigrations[F[_]: Async](conf: Conf): Resource[F, DoobieDatabase[F]] =
    Resource.eval(migrate(conf)).flatMap { _ => default(conf) }

  private def migrate[F[_]: Sync](conf: Conf): F[MigrateResult] = Sync[F].delay {
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
  }

  private def dataSource[F[_]: Sync](conf: Conf): Resource[F, HikariDataSource] =
    val hikari = new HikariConfig()
    hikari.setDriverClassName(Conf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    hikari.setMaxLifetime(60.seconds.toMillis)
    hikari.setMaximumPoolSize(conf.maxPoolSize)
    Resource.make(Sync[F].delay {
      log.info(s"Connecting to '${conf.url}' with pool size ${conf.maxPoolSize} as ${conf.user}...")
      HikariDataSource(hikari)
    })(ds => Sync[F].delay(ds.close()))

  private def transactor[F[_]: Async](ds: HikariDataSource): Resource[F, DataSourceTransactor[F]] =
    for ec <- ExecutionContexts.fixedThreadPool[F](16) // connect EC
    yield Transactor.fromDataSource[F](ds, ec)

class DoobieDatabase[F[_]: Async](tx: DataSourceTransactor[F]):
  implicit val logHandler: LogHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      val logger: String => Unit = if processing > 1.seconds then log.info else log.debug
      logger(s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms.")
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }

  def run[T](io: ConnectionIO[T]): F[T] = io.transact(tx)
