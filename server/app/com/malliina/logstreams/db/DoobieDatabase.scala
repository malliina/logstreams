package com.malliina.logstreams.db

import cats.effect.IO._
import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.malliina.logstreams.db.DoobieDatabase.log
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}
import doobie.util.transactor.Transactor.Aux
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import play.api.Logger

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

object DoobieDatabase {
  private val log = Logger(getClass)

  def apply(conf: Conf, ec: ExecutionContext): DoobieDatabase = apply(dataSource(conf), ec)
  def apply(ds: HikariDataSource, ec: ExecutionContext): DoobieDatabase = new DoobieDatabase(ds, ec)
  def withMigrations(conf: Conf, ec: ExecutionContext): DoobieDatabase = {
    migrate(conf)
    apply(conf, ec)
  }

  def migrate(conf: Conf): Int = {
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
  }

  def dataSource(conf: Conf): HikariDataSource = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName(Conf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    hikari.setMaxLifetime(60.seconds.toMillis)
    hikari.setMaximumPoolSize(5)
    log.info(s"Connecting to '${conf.url}'...")
    new HikariDataSource(hikari)
  }
}

class DoobieDatabase(ds: HikariDataSource, val ec: ExecutionContext) {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private val tx: Resource[IO, Aux[IO, DataSource]] = for {
    ec <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
    be <- Blocker[IO] // our blocking EC
  } yield Transactor.fromDataSource[IO](ds, ec, be)
  implicit val logHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      log.info(s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms.")
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }

  def run[T](io: ConnectionIO[T]): Future[T] =
    tx.use(r => io.transact(r)).unsafeToFuture()

  def close(): Unit = ds.close()
}
