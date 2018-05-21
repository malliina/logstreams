package it

import ch.qos.logback.classic.Level
import com.malliina.http.FullUrl
import com.malliina.logstreams.client.{HttpUtil, SocketClient}
import com.malliina.logstreams.models._
import com.malliina.play.auth.BasicCredentials
import com.malliina.play.models.{Password, Username}
import com.malliina.security.SSLUtils
import com.malliina.util.Utils
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
  val testCreds = BasicCredentials(Username(testUser), Password(testPass))

  test("can ping server") {
    Utils.using(AhcWSClient()(components.materializer)) { client =>
      val res = await(client.url(s"http://localhost:$port/ping").get())
      assert(res.status === 200)
      client.close()
    }
  }

  test("can open socket") {
    await(components.users.add(testCreds))
    withSource { client =>
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

    await(components.users.add(testCreds))
    withSource { source =>
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
        assert(event.source.name === AppName(testUser))
        assert(event.event.message === message)
      }
    }
  }

  test("admin receives status on connect and updates when a source connects and disconnects") {
    val status = Promise[JsValue]()
    val update = Promise[JsValue]()
    val disconnectedPromise = Promise[JsValue]()
    withAdmin(json => if (!status.trySuccess(json)) if(!update.trySuccess(json)) disconnectedPromise.trySuccess(json)) { client =>
      assert(client.isConnected)
      val msg = await(status.future).validate[LogSources]
      assert(msg.isSuccess)
      assert(msg.get.sources.size === 0)
      withSource { _ =>
        val upd = await(update.future).validate[LogSources]
        assert(upd.isSuccess)
        val sources = upd.get.sources
        assert(sources.size === 1)
        assert(sources.head.name.name === testUser)
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

  def withAdmin[T](onJson: JsValue => Any)(code: SocketClient => T) =
    withWebSocket(controllers.routes.SocketsBundle.adminSocket().url, onJson)(code)

  def withListener[T](onJson: JsValue => Any)(code: SocketClient => T) =
    withWebSocket(controllers.routes.SocketsBundle.listenerSocket().url, onJson)(code)

  def withSource[T](code: SocketClient => T) =
    withWebSocket(controllers.routes.SocketsBundle.sourceSocket().url, _ => ())(code)

  def withWebSocket[T](path: String, onJson: JsValue => Any)(code: TestSocket => T) = {
    val wsUri = FullUrl("ws", s"localhost:$port", path)
    Utils.using(new TestSocket(wsUri, onJson)) { client =>
      await(client.initialConnection)
      code(client)
    }
  }

  class TestSocket(wsUri: FullUrl, onJson: JsValue => Any) extends SocketClient(
    wsUri,
    SSLUtils.trustAllSslContext().getSocketFactory,
    Seq(HttpUtil.Authorization -> HttpUtil.authorizationValue(testUser, "p"))
  ) {
    override def onText(message: String) = onJson(Json.parse(message))
  }

}
