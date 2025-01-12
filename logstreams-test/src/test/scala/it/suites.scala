package it

import cats.effect.kernel.Sync
import cats.effect.{IO, Resource}
import cats.syntax.flatMap.*
import com.comcast.ip4s.port
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.app.AppConf
import com.malliina.config.ConfigError
import com.malliina.database.{Conf, DoobieDatabase}
import com.malliina.http.FullUrl
import com.malliina.http.UrlSyntax.url
import com.malliina.http.io.HttpClientIO
import com.malliina.logstreams.auth.*
import com.malliina.logstreams.http4s.{Http4sAuth, Server, ServerComponents}
import com.malliina.logstreams.{LocalConf, LogstreamsConf}
import com.malliina.values.{Password, Username}
import munit.AnyFixture
import org.testcontainers.utility.DockerImageName

class LogsAppConf(override val database: Conf) extends AppConf:
  override def close(): Unit = ()

case class TestDatabase(conf: Conf, container: Option[MySQLContainer])

object DatabaseUtils:
  private def acquire = IO.delay:
    val localTestDb = testConf().map(conf => TestDatabase(conf, None))
    localTestDb.getOrElse:
      val image = DockerImageName.parse("mysql:8.0.33")
      val c = MySQLContainer(mysqlImageVersion = image)
      c.start()
      TestDatabase(
        Conf(
          FullUrl.build(c.jdbcUrl).toOption.get,
          c.username,
          Password(c.password),
          c.driverClassName,
          maxPoolSize = 2,
          autoMigrate = true
        ),
        Option(c)
      )
  val testDatabase: Resource[IO, TestDatabase] = Resource.make(acquire): cont =>
    truncateTestData(cont.conf) >>
      IO.delay:
        cont.container.foreach(_.stop())

  private def testConf(): Either[ConfigError, Conf] =
    LocalConf.conf
      .parse[Password]("logstreams.testdb.pass")
      .map: pass =>
        Conf(
          url"jdbc:mysql://localhost:3306/testlogstreams",
          "testlogstreams",
          pass,
          Conf.MySQLDriver,
          maxPoolSize = 2,
          autoMigrate = true
        )

  private def truncateTestData(conf: Conf): IO[Int] =
    import doobie.implicits.*
    DoobieDatabase
      .default[IO](conf)
      .use: database =>
        for
          l <- database.run(sql"delete from LOGS".update.run)
          u <- database.run(sql"delete from USERS".update.run)
        yield l + u

trait MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val db = ResourceSuiteLocalFixture("database", DatabaseUtils.testDatabase)
  override def munitFixtures: Seq[AnyFixture[?]] = Seq(db)

trait ServerSuite extends MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val http = ResourceFunFixture(HttpClientIO.resource[IO])
  val conf = LogstreamsConf.parseIO[IO].map(_.copy(isTest = true, db = db().conf))
  val testResource = Resource
    .eval(conf)
    .flatMap: conf =>
      Server.server(conf, testAuths, port"12345")
  val server = ResourceSuiteLocalFixture("server", testResource)

  def testAuths: AuthBuilder = new AuthBuilder:
    override def apply[F[_]: Sync](users: UserService[F], web: Http4sAuth[F]) =
      TestAuther(users, web, Username("u"))

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(db, server)

abstract class TestServerSuite extends munit.CatsEffectSuite with ServerSuite

class TestAuther[F[_]: Sync](users: UserService[F], val web: Http4sAuth[F], testUser: Username)
  extends Auther[F]:
  override def sources: Http4sAuthenticator[F, Username] = Auths.sources(users)
  override def viewers: Http4sAuthenticator[F, Username] = hs => Sync[F].pure(Right(testUser))
