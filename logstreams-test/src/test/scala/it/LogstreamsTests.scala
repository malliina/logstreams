package it

import cats.effect.IO
import com.malliina.http.FullUrl
import com.malliina.logstreams.auth.BasicCredentials
import com.malliina.logstreams.client.{HttpUtil, SocketClient}
import com.malliina.logstreams.http4s.LogRoutes
import com.malliina.logstreams.models.*
import com.malliina.values.{Password, Username}
import io.circe.Json
import io.circe.syntax.EncoderOps
import io.circe.parser.parse
import it.LogstreamsTests.testUsername
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.{Status, Uri}

import javax.net.ssl.SSLContext
import scala.concurrent.Promise

object LogstreamsTests {
  val testUsername = Username("u")
}

class LogstreamsTests extends TestServerSuite {
  val testUser = testUsername.name
  val testPass = "p"
  val testCreds = creds(testUser)

  def creds(u: String) = BasicCredentials(Username(u), Password(testPass))
  def port = server().server.address.getPort
  def components = server().app
  def users = components.users

  test("can ping server") {
    val response = BlazeClientBuilder[IO](munitExecutionContext).resource
      .use { client =>
        client.get(Uri.unsafeFromString(s"http://localhost:$port/ping"))(res => IO.pure(res))
      }
      .unsafeRunSync()
    assertEquals(response.status, Status.Ok)
  }

  test("can open socket") {
    val c = creds("u1")
    users.add(c).unsafeRunSync()
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
      LogLevel.Info,
      None
    )

    val user = "u2"
    users.add(creds(user)).unsafeRunSync()
    users.add(testCreds).unsafeRunSync()
    withSource(user) { source =>
      await(source.initialConnection)
      val p = Promise[Json]()
      withListener(p.success) { listener =>
        await(listener.initialConnection)
        source.send(LogEvents(List(testEvent)).asJson.noSpaces)
        val receivedEvent = await(p.future)
        val jsonResult = receivedEvent.as[AppLogEvents]
        assert(jsonResult.isRight)
        val events = jsonResult.toOption.get
        assertEquals(events.events.size, 1)
        val event = events.events.head
        assertEquals(event.source.name, AppName(user))
        assertEquals(event.event.message, message)
      }
    }
  }

  test("admin receives status on connect and updates when a source connects and disconnects") {
    val status = Promise[Json]()
    val update = Promise[Json]()
    val disconnectedPromise = Promise[Json]()

    val user = "u3"
    components.users.add(creds(user)).unsafeRunSync()

    def onJson(json: Json): Unit = {
      if (!status.trySuccess(json))
        if (!update.trySuccess(json)) if (!disconnectedPromise.trySuccess(json)) ()
    }

    withAdmin(onJson) { client =>
      assert(client.isConnected)
      val msg = await(status.future).as[LogSources]
      assert(msg.isRight)
      assert(msg.toOption.get.sources.isEmpty)
      withSource(user) { _ =>
        val upd = await(update.future).as[LogSources]
        assert(upd.isRight)
        val sources = upd.toOption.get.sources
        assertEquals(sources.size, 1)
        assertEquals(sources.head.name.name, user)
        val status = Promise[Json]()
        withAdmin(status.success) { client =>
          val statusUpdate = await(status.future).as[LogSources].toOption.get
          assert(statusUpdate.sources.nonEmpty)
        }
      }
      val disconnectUpdate = await(disconnectedPromise.future).as[LogSources]
      assert(disconnectUpdate.isRight)
      assert(disconnectUpdate.toOption.get.sources.isEmpty)
    }
  }

  def withAdmin[T](onJson: Json => Any)(code: SocketClient => T) =
    withWebSocket(testUser, LogRoutes.sockets.admins, onJson)(code)

  def withListener[T](onJson: Json => Any)(code: SocketClient => T) =
    withWebSocket(testUser, LogRoutes.sockets.logs, onJson)(code)

  def withSource[T](username: String)(code: SocketClient => T) =
    withWebSocket(username, LogRoutes.sockets.sources, _ => ())(code)

  def withWebSocket[T](username: String, path: Uri, onJson: Json => Any)(
    code: TestSocket => T
  ) = {
    val wsUri = FullUrl("ws", s"localhost:$port", path.renderString)
    using(new TestSocket(wsUri, onJson, username)) { client =>
      await(client.initialConnection)
      code(client)
    }
  }

  def using[T <: AutoCloseable, U](res: T)(code: T => U) =
    try code(res)
    finally res.close()

  class TestSocket(wsUri: FullUrl, onJson: Json => Any, username: String = testUser)
    extends SocketClient(
      wsUri,
      SSLContext.getDefault.getSocketFactory,
      Seq(HttpUtil.Authorization -> HttpUtil.authorizationValue(username, testPass))
    ) {
    override def onText(message: String): Unit =
      parse(message).fold(err => println(err), ok => onJson(ok))
  }
}
