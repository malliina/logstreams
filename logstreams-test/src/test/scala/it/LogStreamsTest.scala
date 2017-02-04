package it

import java.net.URI

import ch.qos.logback.classic.Level
import com.malliina.logbackrx.LogEvent
import com.malliina.logstreams.client.{HttpUtil, KeyValue, SocketClient}
import com.malliina.logstreams.models.AppName
import com.malliina.security.SSLUtils
import com.malliina.util.Utils
import org.scalatest.FunSuite
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponents
import play.api.libs.json.Json
import play.api.libs.ws.ahc.AhcWSClient
import tests.{OneServerPerSuite2, TestComponents}

abstract class TestServerSuite extends ServerSuite(new TestComponents(_))

abstract class ServerSuite[T <: BuiltInComponents](build: Context => T)
  extends FunSuite
    with OneServerPerSuite2[T] {
  override def createComponents(context: Context) = build(context)
}

class LogStreamsTest extends TestServerSuite {
  test("can read component") {
    assert(components.home.replaySize === 10)
  }

  test("can ping server") {
    Utils.using(AhcWSClient()(components.materializer)) { client =>
      val res = await(client.url(s"http://localhost:$port/ping").get())
      assert(res.status === 200)
      client.close()
    }
  }

  test("can open socket") {
    withSocket { client =>
      val uri = await(client.initialConnection)
      assert(uri.toString.nonEmpty)
    }
  }

  test("sent message is received on the server") {
    val message = "hello, world"
    val testEvent = LogEvent(
      System.currentTimeMillis(),
      "now",
      message,
      getClass.getName.stripSuffix("$"),
      "this thread",
      Level.INFO,
      None)

    withSocket { client =>
      await(client.initialConnection)
      client send Json.stringify(Json.toJson(testEvent))
      val receivedEvent = components.home.events.toBlocking.first
      assert(receivedEvent.source.name === AppName("u"))
      assert(receivedEvent.event.message === message)
    }
  }

  def withSocket[T](code: SocketClient => T) = {
    val path = "/ws/sources"
    val wsUri = new URI(s"ws://localhost:$port$path")
    val sf = SSLUtils.trustAllSslContext().getSocketFactory
    val headers: Seq[KeyValue] = Seq(HttpUtil.Authorization -> HttpUtil.authorizationValue("u", "p"))

    Utils.using(new SocketClient(wsUri, sf, headers)) { client =>
      code(client)
    }
  }
}