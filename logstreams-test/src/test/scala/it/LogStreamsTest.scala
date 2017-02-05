package it

import java.net.URI

import ch.qos.logback.classic.Level
import com.malliina.logbackrx.LogEvent
import com.malliina.logstreams.client.{HttpUtil, KeyValue, SocketClient}
import com.malliina.logstreams.models.{AppName, LogEvents}
import com.malliina.play.auth.BasicCredentials
import com.malliina.play.models.{Password, Username}
import com.malliina.security.SSLUtils
import com.malliina.util.Utils
import org.scalatest.FunSuite
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponents, Logger}
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
  val testUser = "u"
  val testPass = "p"
  val testCreds = BasicCredentials(Username(testUser), Password(testPass))

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
    await(components.users.add(testCreds))
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

    await(components.users.add(testCreds))
    withSocket { client =>
      await(client.initialConnection)
      client send Json.stringify(Json.toJson(LogEvents(Seq(testEvent))))
      val receivedEvent = components.home.events.toBlocking.first.events.head
      assert(receivedEvent.source.name === AppName(testUser))
      assert(receivedEvent.event.message === message)
    }
  }

  test("logback appender") {
    Logger("test").error("This is a test event")
    Thread sleep 100
  }

  def withSocket[T](code: SocketClient => T) = {
    val path = "/ws/sources"
    val wsUri = new URI(s"ws://localhost:$port$path")
    val sf = SSLUtils.trustAllSslContext().getSocketFactory
    val headers: Seq[KeyValue] = Seq(HttpUtil.Authorization -> HttpUtil.authorizationValue(testUser, "p"))

    Utils.using(new SocketClient(wsUri, sf, headers)) { client =>
      code(client)
    }
  }
}
