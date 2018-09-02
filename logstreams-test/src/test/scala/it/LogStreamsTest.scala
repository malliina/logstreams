package it

import ch.qos.logback.classic.Level
import com.malliina.http.FullUrl
import com.malliina.logstreams.client.{HttpUtil, SocketClient}
import com.malliina.logstreams.models._
import com.malliina.play.auth.BasicCredentials
import com.malliina.security.SSLUtils
import com.malliina.values.{Password, Username}
import org.scalatest.FunSuite
import play.api.ApplicationLoader.Context
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.{BuiltInComponents, Logger}
import tests.{OneServerPerSuite2, TestComponents}

import scala.concurrent.Promise

abstract class TestServerSuite extends ServerSuite(new TestComponents(_))

abstract class ServerSuite[T <: BuiltInComponents](build: Context => T)
  extends FunSuite
    with OneServerPerSuite2[T] {
  override def createComponents(context: Context) = build(context)
}

class LogStreamsTest extends TestServerSuite {
  val testUser = "u"
  val testPass = "p"
  val testCreds = creds(testUser)

  def creds(u: String) = BasicCredentials(Username(u), Password(testPass))

  test("can ping server") {
    using(AhcWSClient()(components.materializer)) { client =>
      val res = await(client.url(s"http://localhost:$port/ping").get())
      assert(res.status === 200)
      client.close()
    }
  }

  test("can open socket") {
    val c = creds("u1")
    await(components.users.add(c))
    withSource(c.username.name) { client =>
      val uri = await(client.initialConnection)
      assert(uri.toString.nonEmpty)
    }
  }

  test("sent message is received by listener") {
    val message = "hello, world"
    val testEvent = LogEvent(
      System.currentTimeMillis(),
      "now",
      message,
      getClass.getName.stripSuffix("$"),
      "this thread",
      Level.INFO.levelStr,
      None
    )

    val user = "u2"
    await(components.users.add(creds(user)))
    await(components.users.add(testCreds))
    withSource(user) { source =>
      await(source.initialConnection)
      val p = Promise[JsValue]()
      withListener(p.success) { listener =>
        await(listener.initialConnection)
        source send Json.stringify(Json.toJson(LogEvents(Seq(testEvent))))
        val receivedEvent = await(p.future)
        val jsonResult = receivedEvent.validate[AppLogEvents]
        assert(jsonResult.isSuccess)
        val events = jsonResult.get
        assert(events.events.size === 1)
        val event = events.events.head
        assert(event.source.name === AppName(user))
        assert(event.event.message === message)
      }
    }
  }

  test("admin receives status on connect and updates when a source connects and disconnects") {
    val status = Promise[JsValue]()
    val update = Promise[JsValue]()
    val disconnectedPromise = Promise[JsValue]()

    val user = "u3"
    await(components.users.add(creds(user)))

    def onJson(json: JsValue) = {
      if (!status.trySuccess(json)) if (!update.trySuccess(json)) if (!disconnectedPromise.trySuccess(json)) ()
    }

    withAdmin(onJson) { client =>
      assert(client.isConnected)
      val msg = await(status.future).validate[LogSources]
      assert(msg.isSuccess)
      assert(msg.get.sources.size === 0)
      withSource(user) { _ =>
        val upd = await(update.future).validate[LogSources]
        assert(upd.isSuccess)
        val sources = upd.get.sources
        assert(sources.size === 1)
        assert(sources.head.name.name === user)
      }
      val disconnectUpdate = await(disconnectedPromise.future).validate[LogSources]
      assert(disconnectUpdate.isSuccess)
      assert(disconnectUpdate.get.sources.isEmpty)
    }
  }

  ignore("logback appender") {
    Logger("test").error("This is a test event")
    Thread sleep 100
  }

  val bundle = controllers.routes.SocketsBundle

  def withAdmin[T](onJson: JsValue => Any)(code: SocketClient => T) =
    withWebSocket(testUser, bundle.adminSocket().url, onJson)(code)

  def withListener[T](onJson: JsValue => Any)(code: SocketClient => T) =
    withWebSocket(testUser, bundle.listenerSocket().url, onJson)(code)

  def withSource[T](username: String)(code: SocketClient => T) =
    withWebSocket(username, bundle.sourceSocket().url, _ => ())(code)

  def withWebSocket[T](username: String, path: String, onJson: JsValue => Any)(code: TestSocket => T) = {
    val wsUri = FullUrl("ws", s"localhost:$port", path)
    using(new TestSocket(wsUri, onJson, username)) { client =>
      await(client.initialConnection)
      code(client)
    }
  }

  def using[T <: AutoCloseable, U](res: T)(code: T => U) = try {
    code(res)
  } finally {
    res.close()
  }

  class TestSocket(wsUri: FullUrl, onJson: JsValue => Any, username: String = testUser) extends SocketClient(
    wsUri,
    SSLUtils.trustAllSslContext().getSocketFactory,
    Seq(HttpUtil.Authorization -> HttpUtil.authorizationValue(username, "p"))
  ) {
    override def onText(message: String): Unit = onJson(Json.parse(message))
  }

}
