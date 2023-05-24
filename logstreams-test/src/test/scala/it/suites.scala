package it

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.effect.kernel.Sync
import cats.syntax.flatMap.*
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.app.AppConf
import com.malliina.logstreams.auth.*
import com.malliina.logstreams.db.{Conf, DoobieDatabase}
import com.malliina.logstreams.http4s.{Http4sAuth, Server, ServerComponents}
import com.malliina.logstreams.{LocalConf, LogstreamsConf}
import com.malliina.values.Username
import munit.FunSuite
import org.testcontainers.utility.DockerImageName
import com.comcast.ip4s.port
import com.malliina.http.io.HttpClientIO

import scala.concurrent.Promise
import scala.util.Try

class LogsAppConf(override val database: Conf) extends AppConf:
  override def close(): Unit = ()

case class TestDatabase(conf: Conf, container: Option[MySQLContainer])

object DatabaseUtils:
  val testDatabase: Resource[IO, TestDatabase] = Resource.make {
    IO.delay {
      val localTestDb = testConf().map { conf => TestDatabase(conf, None) }
      localTestDb.getOrElse {
        val image = DockerImageName.parse("mysql:8.0.33")
        val c = MySQLContainer(mysqlImageVersion = image)
        c.start()
        TestDatabase(
          Conf(
            c.jdbcUrl,
            c.username,
            c.password,
            c.driverClassName,
            maxPoolSize = 2,
            autoMigrate = true
          ),
          Option(c)
        )
      }
    }
  } { cont =>
    truncateTestData(cont.conf) >>
      IO.delay {
        cont.container.foreach(_.stop())
      }
  }

  private def testConf(): Either[Throwable, Conf] =
    Try(
      LogstreamsConf.parseDatabase(LocalConf.conf.getConfig("logstreams").getConfig("testdb"))
    ).toEither

  private def truncateTestData(conf: Conf): IO[Int] =
    import doobie.implicits.*
    DoobieDatabase
      .default[IO](conf)
      .use { database =>
        for
          l <- database.run(sql"delete from LOGS".update.run)
          u <- database.run(sql"delete from USERS".update.run)
        yield l + u
      }

trait MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val db = ResourceSuiteLocalFixture("database", DatabaseUtils.testDatabase)
  override def munitFixtures: Seq[Fixture[?]] = Seq(db)

trait ServerSuite extends MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val http = ResourceFixture(HttpClientIO.resource)
  val conf = IO.delay(LogstreamsConf.parse().copy(db = db().conf))
  val testResource = Resource.eval(conf).flatMap { conf =>
    Server.server(conf, testAuths, port"12345")
  }
  val server = ResourceSuiteLocalFixture("server", testResource)

  def testAuths: AuthBuilder = new AuthBuilder:
    override def apply[F[_]: Sync](users: UserService[F], web: Http4sAuth[F]) =
      TestAuther(users, web, Username("u"))

  override def munitFixtures: Seq[Fixture[?]] = Seq(db, server)

abstract class TestServerSuite extends munit.CatsEffectSuite with ServerSuite

class TestAuther[F[_]: Sync](users: UserService[F], val web: Http4sAuth[F], testUser: Username)
  extends Auther[F]:
  override def sources: Http4sAuthenticator[F, Username] = Auths.sources(users)
  override def viewers: Http4sAuthenticator[F, Username] = hs => Sync[F].pure(Right(testUser))
