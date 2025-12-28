package it

import cats.effect.kernel.Sync
import cats.effect.{IO, Resource}
import ch.qos.logback.classic.Level
import com.comcast.ip4s.port
import com.malliina.app.AppConf
import com.malliina.config.ConfigNode
import com.malliina.database.{Conf, DoobieDatabase}
import com.malliina.http.FullUrl
import com.malliina.http.UrlSyntax.url
import com.malliina.http.io.HttpClientIO
import com.malliina.logback.LogbackUtils
import com.malliina.logstreams.auth.*
import com.malliina.logstreams.http4s.{AppResources, Http4sAuth, SocketInfo}
import com.malliina.logstreams.models.{AppName, LogClientId}
import com.malliina.logstreams.{LocalConf, LogstreamsConf}
import com.malliina.values.{Password, Username}
import munit.AnyFixture

class LogsAppConf(override val database: Conf) extends AppConf:
  override def close(): Unit = ()

object DatabaseUtils:
  val testConf = LocalConf.local("test-logstreams.conf")

  private def acquire = IO.delay:
    val either = testConf
      .parse[Password]("logstreams.db.pass")
      .map: pass =>
        testDatabaseConf(pass)
    either.fold(err => throw err, identity)
  val testDatabase: Resource[IO, Conf] = Resource.make(acquire): conf =>
    truncateTestData(conf).void

  private def testDatabaseConf(password: Password): Conf =
    Conf(
      url"jdbc:mariadb://127.0.0.1:3306/testlogstreams",
      "testlogstreams",
      password,
      LogstreamsConf.mariaDbDriver,
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
  object TestServer extends AppResources:
    LogbackUtils.init(rootLevel = Level.INFO)
  val http = ResourceFunFixture(HttpClientIO.resource[IO])
  val conf = for
    database <- IO(db())
    node <- IO.fromEither(DatabaseUtils.testConf.parse[ConfigNode]("logstreams"))
    conf <- IO.fromEither(LogstreamsConf.parse(node, pass => database, isTest = true))
  yield conf
  val testResource = Resource
    .eval(conf)
    .flatMap: conf =>
      TestServer.server(conf, testAuths, port"12345")
  val server = ResourceSuiteLocalFixture("server", testResource)

  def testAuths: AuthBuilder = new AuthBuilder:
    override def apply[F[_]: Sync](users: UserService[F], web: Http4sAuth[F]) =
      TestAuther(users, web, Username("u"))

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(db, server)

abstract class TestServerSuite extends munit.CatsEffectSuite with ServerSuite

class TestAuther[F[_]: Sync](users: UserService[F], val web: Http4sAuth[F], testUser: Username)
  extends Auther[F]:
  override def sources: HeaderAuthenticator[F, Username] = Auths.sources(users)
  override def viewers: HeaderAuthenticator[F, Username] = hs => Sync[F].pure(Right(testUser))
  override def public: RequestAuthenticator[F, SocketInfo] = req =>
    Sync[F].pure(Right(SocketInfo(AppName.fromUsername(testUser), LogClientId.random())))
