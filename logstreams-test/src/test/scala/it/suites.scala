package it

import cats.effect.unsafe.implicits.global
import cats.effect.IO
import cats.syntax.flatMap._
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.app.AppConf
import com.malliina.logstreams.auth._
import com.malliina.logstreams.db.{Conf, DoobieDatabase}
import com.malliina.logstreams.http4s.{Http4sAuth, Server, ServerComponents}
import com.malliina.logstreams.{LocalConf, LogstreamsConf}
import com.malliina.values.Username
import munit.FunSuite
import org.testcontainers.utility.DockerImageName

import scala.concurrent.Promise
import scala.util.Try

class LogsAppConf(override val database: Conf) extends AppConf {
  override def close(): Unit = ()
}

case class TestConf(testdb: Conf)
case class WrappedTestConf(logstreams: TestConf)

trait MUnitDatabaseSuite { self: munit.Suite =>
  val db: Fixture[Conf] = new Fixture[Conf]("database") {
    var container: Option[MySQLContainer] = None
    var conf: Option[Conf] = None
    def apply() = conf.get
    override def beforeAll(): Unit = {
      val localTestDb = testConf()
      val testDb = localTestDb.getOrElse {
        val image = DockerImageName.parse("mysql:5.7.29")
        val c = MySQLContainer(mysqlImageVersion = image)
        c.start()
        container = Option(c)
        Conf(s"${c.jdbcUrl}?useSSL=false", c.username, c.password, c.driverClassName)
      }
      conf = Option(testDb)
    }
    override def afterAll(): Unit = {
      truncateTestData()
      container.foreach(_.stop())
    }
  }

  private def truncateTestData() = {
    import doobie.implicits._
    DoobieDatabase(db())
      .use { database =>
        for {
          l <- database.run(sql"delete from LOGS".update.run)
          u <- database.run(sql"delete from USERS".update.run)
        } yield l + u
      }
      .unsafeRunSync()
  }

  private def testConf(): Either[Throwable, Conf] = {
    Try(
      LogstreamsConf.parseDatabase(LocalConf.localConf.getConfig("logstreams").getConfig("testdb"))
    ).toEither
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db)
}

trait ServerSuite extends MUnitDatabaseSuite { self: munit.Suite =>
  val server: Fixture[ServerComponents] = new Fixture[ServerComponents]("server") {
    private var service: Option[ServerComponents] = None
    val promise = Promise[IO[Unit]]()

    override def apply(): ServerComponents = service.get

    override def beforeAll(): Unit = {
      val testConf = LogstreamsConf.parse().copy(db = db())
      val resource = Server.server(testConf, testAuths, port = 12345)
      val setupEffect = resource.allocated
        .map {
          case (t, release) =>
            promise.success(release)
            t
        }
        .flatTap(t => IO.pure(()))

      service = Option(setupEffect.unsafeRunSync())
    }

    override def afterAll(): Unit = {
      IO.fromFuture(IO(promise.future)).flatten.unsafeRunSync()
    }
  }

  def testAuths = new AuthBuilder {
    override def apply(users: UserService[IO], web: Http4sAuth): Auther =
      new TestAuther(users, web, Username("u"))
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db, server)
}

abstract class TestServerSuite extends FunSuite with ServerSuite

class TestAuther(users: UserService[IO], val web: Http4sAuth, testUser: Username) extends Auther {
  override def sources: Http4sAuthenticator[IO, Username] = Auths.sources(users)
  override def viewers: Http4sAuthenticator[IO, Username] = hs => IO.pure(Right(testUser))
}
