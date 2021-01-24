package it

import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.app.AppConf
import com.malliina.http.FullUrl
import com.malliina.logstreams.client.{HttpUtil, SocketClient}
import com.malliina.logstreams.db.Conf
import munit.FunSuite
import play.api.libs.json.{JsValue, Json}

import javax.net.ssl.SSLContext

class LogsAppConf(override val database: Conf) extends AppConf {
  override def close(): Unit = ()
}

//case class LogsTestInstance(server: RunningServer, components: TestComponents)

trait MUnitDatabaseSuite { self: munit.Suite =>
  val db: Fixture[Conf] = new Fixture[Conf]("database") {
    var container: Option[MySQLContainer] = None
    var conf: Option[Conf] = None
    def apply() = conf.get
    override def beforeAll(): Unit = {
//      val localTestDb =
//        Try(LocalConf.localConf.get[Configuration]("logstreams.testdb")).toEither.flatMap { c =>
//          Conf.fromDatabaseConf(c)
//        }
//      val testDb = localTestDb.getOrElse {
//        val c = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
//        c.start()
//        container = Option(c)
//        Conf(s"${c.jdbcUrl}?useSSL=false", c.username, c.password, c.driverClassName)
//      }
//      conf = Option(testDb)
    }
    override def afterAll(): Unit = container.foreach(_.stop())
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db)
}

//trait LogsServerPerSuite extends MUnitDatabaseSuite { self: Suite =>
//  val testServer: Fixture[LogsTestInstance] = new Fixture[LogsTestInstance]("test-server") {
//    private var runningServer: Option[LogsTestInstance] = None
//    def apply() = runningServer.get
//    override def beforeAll(): Unit = {
//      val comps = new TestComponents(TestAppLoader.createTestAppContext, new LogsAppConf(db()))
//      runningServer = Option(
//        LogsTestInstance(DefaultTestServerFactory.start(comps.application), comps)
//      )
//      Await.result(comps.truncator.truncate(), 10.seconds)
//    }
//    override def afterAll(): Unit = {
//      runningServer.foreach(_.server.stopServer.close())
//    }
//  }
//  def components = testServer().components
//  def port = testServer().server.endpoints.httpEndpoint.map(_.port).get
//
//  override def munitFixtures: Seq[Fixture[_]] = Seq(db, testServer)
//}

abstract class TestServerSuite extends FunSuite // with LogsServerPerSuite

class LogStreamsTest extends TestServerSuite {
  val testUser = "u"
  val testPass = "p"
//  val testCreds = creds(testUser)
//
//  def creds(u: String) = BasicCredentials(Username(u), Password(testPass))
//
//  implicit val as = ActorSystem("test")
//
//  test("can ping server") {
//    using(AhcWSClient()) { client =>
//      val res = await(client.url(s"http://localhost:$port/ping").get())
//      assert(res.status == 200)
//      client.close()
//    }
//  }
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
