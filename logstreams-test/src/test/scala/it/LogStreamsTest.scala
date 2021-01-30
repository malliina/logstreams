package it

import cats.effect.{ContextShift, IO}
import cats.syntax.flatMap._
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.app.AppConf
import com.malliina.http.FullUrl
import com.malliina.logstreams.client.{HttpUtil, SocketClient}
import com.malliina.logstreams.db.Conf
import com.malliina.logstreams.http4s.{Server, Service}
import com.malliina.logstreams.{LocalConf, LogstreamsConf}
import munit.FunSuite
import org.http4s.{Status, Uri}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.server.Server
import play.api.libs.json.{JsValue, Json}
import pureconfig.error.ConfigReaderFailures
import pureconfig.{ConfigObjectSource, ConfigSource}

import javax.net.ssl.SSLContext
import scala.concurrent.Promise

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
      println(localTestDb)
      val testDb = localTestDb.getOrElse {
        val c = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
        c.start()
        container = Option(c)
        Conf(s"${c.jdbcUrl}?useSSL=false", c.username, c.password, c.driverClassName)
      }
      conf = Option(testDb)
    }
    override def afterAll(): Unit = container.foreach(_.stop())
  }

  private def testConf(): Either[ConfigReaderFailures, Conf] = {
    import pureconfig.generic.auto.exportReader
    ConfigObjectSource(Right(LocalConf.localConf))
      .withFallback(ConfigSource.default)
      .load[WrappedTestConf]
      .map(_.logstreams.testdb)
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db)
}

trait DatabaseAppSuite extends MUnitDatabaseSuite { self: munit.Suite =>
  implicit def munitContextShift: ContextShift[IO] =
    IO.contextShift(munitExecutionContext)

  val app: Fixture[Service.Routes] = new Fixture[Service.Routes]("database-app") {
    private var service: Option[Service.Routes] = None
    val promise = Promise[IO[Unit]]()

    override def apply(): Service.Routes = service.get

    override def beforeAll(): Unit = {
      val testConf = LogstreamsConf.load.copy(db = db())
      val resource = Server.appResource(testConf)
      val resourceEffect = resource.allocated[IO, Service.Routes]
      val setupEffect =
        resourceEffect
          .map {
            case (t, release) =>
              promise.success(release)
              t
          }
          .flatTap(t => IO.pure(()))

      service = Option(setupEffect.unsafeRunSync())
    }

    override def afterAll(): Unit = {
      IO.fromFuture(IO(promise.future))(munitContextShift).flatten.unsafeRunSync()
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db, app)
}

trait ServerSuite extends MUnitDatabaseSuite { self: munit.Suite =>
  implicit def munitContextShift: ContextShift[IO] =
    IO.contextShift(munitExecutionContext)

  val server: Fixture[Server[IO]] = new Fixture[Server[IO]]("server") {
    private var service: Option[Server[IO]] = None
    val promise = Promise[IO[Unit]]()

    override def apply(): Server[IO] = service.get

    override def beforeAll(): Unit = {
      val testConf = LogstreamsConf.load.copy(db = db())
      val resource = Server.server(testConf, port = 12345)
      val resourceEffect = resource.allocated[IO, Server[IO]]
      val setupEffect =
        resourceEffect
          .map {
            case (t, release) =>
              promise.success(release)
              t
          }
          .flatTap(t => IO.pure(()))

      service = Option(setupEffect.unsafeRunSync())
    }

    override def afterAll(): Unit = {
      IO.fromFuture(IO(promise.future))(munitContextShift).flatten.unsafeRunSync()
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db, server)
}

abstract class TestServerSuite extends FunSuite with ServerSuite

class LogStreamsTest extends TestServerSuite {
  val testUser = "u"
  val testPass = "p"
//  val testCreds = creds(testUser)
//
//  def creds(u: String) = BasicCredentials(Username(u), Password(testPass))
//
//  implicit val as = ActorSystem("test")
//
  def port = server().address.getPort

  test("can ping server") {
    val response = BlazeClientBuilder[IO](munitExecutionContext).resource
      .use { client =>
        client.get(Uri.unsafeFromString(s"http://localhost:$port/ping"))(res => IO.pure(res))
      }
      .unsafeRunSync()
    assertEquals(response.status, Status.Ok)
  }
//
//  test("can open socket") {
//    val c = creds("u1")
//    await(components.users.add(c))
//    withSource(c.username.name) { client =>
//      val uri = await(client.initialConnection)
//      assert(uri.toString.nonEmpty)
//    }
//  }
//
//  test("sent message is received by listener") {
//    val message = "hello, world"
//    val testEvent = LogEvent(
//      System.currentTimeMillis(),
//      "now",
//      message,
//      getClass.getName.stripSuffix("$"),
//      "this thread",
//      Level.INFO.levelStr,
//      None
//    )
//
//    val user = "u2"
//    await(components.users.add(creds(user)))
//    await(components.users.add(testCreds))
//    withSource(user) { source =>
//      await(source.initialConnection)
//      val p = Promise[JsValue]()
//      withListener(p.success) { listener =>
//        await(listener.initialConnection)
//        source send Json.stringify(Json.toJson(LogEvents(List(testEvent))))
//        val receivedEvent = await(p.future)
//        val jsonResult = receivedEvent.validate[AppLogEvents]
//        assert(jsonResult.isSuccess)
//        val events = jsonResult.get
//        assert(events.events.size == 1)
//        val event = events.events.head
//        assert(event.source.name == AppName(user))
//        assert(event.event.message == message)
//      }
//    }
//  }
//
//  test(
//    "admin receives status on connect and updates when a source connects and disconnects".ignore
//  ) {
//    val status = Promise[JsValue]()
//    val update = Promise[JsValue]()
//    val disconnectedPromise = Promise[JsValue]()
//
//    val user = "u3"
//    await(components.users.add(creds(user)))
//
//    def onJson(json: JsValue): Unit = {
//      if (!status.trySuccess(json))
//        if (!update.trySuccess(json)) if (!disconnectedPromise.trySuccess(json)) ()
//    }
//
//    withAdmin(onJson) { client =>
//      assert(client.isConnected)
//      val msg = await(status.future).validate[LogSources]
//      assert(msg.isSuccess)
//      assert(msg.get.sources.isEmpty)
//      withSource(user) { _ =>
//        val upd = await(update.future).validate[LogSources]
//        assert(upd.isSuccess)
//        val sources = upd.get.sources
//        assert(sources.size == 1)
//        assert(sources.head.name.name == user)
//      }
//      val disconnectUpdate = await(disconnectedPromise.future).validate[LogSources]
//      assert(disconnectUpdate.isSuccess)
//      assert(disconnectUpdate.get.sources.isEmpty)
//    }
//  }
//
//  test("logback appender".ignore) {
//    Logger("test").error("This is a test event")
//    Thread sleep 100
//  }
//
//  val bundle = controllers.routes.SocketsBundle
//
//  def withAdmin[T](onJson: JsValue => Any)(code: SocketClient => T) =
//    withWebSocket(testUser, bundle.adminSocket().url, onJson)(code)
//
//  def withListener[T](onJson: JsValue => Any)(code: SocketClient => T) =
//    withWebSocket(testUser, bundle.listenerSocket().url, onJson)(code)
//
//  def withSource[T](username: String)(code: SocketClient => T) =
//    withWebSocket(username, bundle.sourceSocket().url, _ => ())(code)
//
//  def withWebSocket[T](username: String, path: String, onJson: JsValue => Any)(
//    code: TestSocket => T
//  ) = {
//    val wsUri = FullUrl("ws", s"localhost:$port", path)
//    using(new TestSocket(wsUri, onJson, username)) { client =>
//      await(client.initialConnection)
//      code(client)
//    }
//  }

  def using[T <: AutoCloseable, U](res: T)(code: T => U) =
    try code(res)
    finally res.close()

  class TestSocket(wsUri: FullUrl, onJson: JsValue => Any, username: String = testUser)
    extends SocketClient(
      wsUri,
      SSLContext.getInstance("TLS").getSocketFactory,
      Seq(HttpUtil.Authorization -> HttpUtil.authorizationValue(username, "p"))
    ) {
    override def onText(message: String): Unit = onJson(Json.parse(message))
  }

}
