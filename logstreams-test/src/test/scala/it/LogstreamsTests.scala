package it

import cats.effect.unsafe.implicits.global
import cats.effect.{Deferred, IO}
import com.malliina.http.FullUrl
import com.malliina.http.io.SocketEvent.Open
import com.malliina.http.io.{HttpClientF2, HttpClientIO, WebSocketF}
import com.malliina.logstreams.auth.BasicCredentials
import com.malliina.logstreams.client.{HttpUtil, KeyValue}
import com.malliina.logstreams.http4s.LogRoutes
import com.malliina.logstreams.models.*
import com.malliina.values.{Password, Username}
import com.malliina.util.AppLogger
import io.circe.Json
import io.circe.syntax.EncoderOps
import io.circe.parser.parse
import it.LogstreamsTests.testUsername
import org.http4s.{Status, Uri}
import org.slf4j.LoggerFactory

import javax.net.ssl.SSLContext
import scala.concurrent.Promise

object LogstreamsTests:
  val testUsername = Username("u")

class LogstreamsTests extends TestServerSuite:
  val log = AppLogger(getClass)

  val testUser = testUsername.name
  val testPass = "p"
  val testCreds = creds(testUser)

  def creds(u: String) = BasicCredentials(Username(u), Password(testPass))
  def port = server().server.address.getPort
  def components = server().app
  def users = components.users

  http.test("can open socket") { client =>
    val c = creds("u1")
    users.add(c).flatMap { _ =>
      withSource(c.username.name, client) { socket =>
        IO(assertEquals(1, 1))
      }
    }
  }

  http.test("sent message is received by listener") { client =>
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
    val task = withSource(user, client) { source =>
      Deferred[IO, Json].flatMap { p =>
        withListener(client) { listener =>
          val receive: IO[Unit] =
            listener.jsonMessages
              .evalMap(msg => p.complete(msg))
              .take(1)
              .compile
              .drain
          val payload = LogEvents(List(testEvent))
          for
            _ <- receive.start
            _ <- source.send(payload)
            receivedEvent <- p.get
          yield
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
    for
      _ <- users.add(creds(user))
      _ <- users.add(testCreds)
      t <- task
    yield t
  }

  http.test("admin receives status on connect and updates when a source connects and disconnects") {
    client =>
      val user = "u3"
      Deferred[IO, Json].flatMap { status =>
        Deferred[IO, Json].flatMap { adminStatus =>
          Deferred[IO, Json].flatMap { update =>
            Deferred[IO, Json].flatMap { disconnectedPromise =>
              components.users.add(creds(user)).flatMap { _ =>
                withAdmin(client) { adminSocket =>
                  val receive = adminSocket.jsonMessages.evalMap { msg =>
                    for
                      wasStatusEmpty <- status.complete(msg)
                      wasUpdateEmpty <-
                        if wasStatusEmpty then IO.pure(false) else update.complete(msg)
                      _ <-
                        if wasStatusEmpty || wasUpdateEmpty then IO.pure(false)
                        else disconnectedPromise.complete(msg)
                    yield ()
                  }.compile.drain
                  receive.start.flatMap { _ =>
                    status.get.flatMap { statusJson =>
                      val msg = statusJson.as[LogSources]
                      assert(msg.isRight)
                      assert(msg.toOption.get.sources.isEmpty)
                      val task = withSource(user, client) { _ =>
                        update.get.flatMap { updateJson =>
                          val upd = updateJson.as[LogSources]
                          assert(upd.isRight)
                          val sources = upd.toOption.get.sources
                          assertEquals(sources.size, 1)
                          assertEquals(sources.head.name.name, user)
                          withAdmin(client) { client =>
                            val receiveAdmin = client.jsonMessages.evalMap { msg =>
                              adminStatus.complete(msg)
                            }
                            for
                              _ <- receiveAdmin.compile.drain.start
                              adminStatusJson <- adminStatus.get
                            yield
                              val statusUpdate =
                                adminStatusJson.as[LogSources].fold(err => throw err, identity)
                              assert(statusUpdate.sources.nonEmpty)
                          }
                        }
                      }
                      for
                        _ <- task
                        disconnected <- disconnectedPromise.get
                      yield
                        val disconnectUpdate = disconnected.as[LogSources]
                        assert(disconnectUpdate.isRight)
                        assert(disconnectUpdate.toOption.get.sources.isEmpty)
                    }
                  }
                }
              }
            }
          }
        }
      }
  }

  def withAdmin[T](httpClient: HttpClientF2[IO])(code: WebSocketF[IO] => IO[T]) =
    openAuthedSocket(testUser, LogRoutes.sockets.admins, httpClient)(code)

  def withListener[T](httpClient: HttpClientF2[IO])(code: WebSocketF[IO] => IO[T]) =
    openAuthedSocket(testUser, LogRoutes.sockets.logs, httpClient)(code)

  def withSource[T](username: String, httpClient: HttpClientF2[IO])(code: WebSocketF[IO] => IO[T]) =
    openAuthedSocket(username, LogRoutes.sockets.sources, httpClient)(code)

  def openAuthedSocket[T](
    username: String,
    uri: Uri,
    httpClient: HttpClientF2[IO]
  )(
    code: WebSocketF[IO] => IO[T]
  ): IO[T] =
    val wsUrl = FullUrl("ws", s"localhost:$port", uri.renderString)
    val kvs: List[KeyValue] = List(
      HttpUtil.Authorization -> HttpUtil.authorizationValue(username, testPass)
    )
    openSocket(wsUrl, kvs, httpClient)(code)

  def openSocket[T](url: FullUrl, headers: List[KeyValue], httpClient: HttpClientF2[IO])(
    code: WebSocketF[IO] => IO[T]
  ): IO[T] =
    WebSocketF.build[IO](url, headers.map(kv => kv.key -> kv.value).toMap, httpClient.client).use {
      socket =>
        val openEvents = socket.events.collect { case o @ Open(_, _) =>
          o
        }
        openEvents.take(1).compile.toList >> code(socket)
    }

  def using[T <: AutoCloseable, U](res: T)(code: T => U) =
    try code(res)
    finally res.close()

//  class TestSocket(wsUri: FullUrl, onJson: Json => IO[Unit], username: String = testUser)
//    extends SocketClient(
//      wsUri,
//      SSLContext.getDefault.getSocketFactory,
//      Seq(HttpUtil.Authorization -> HttpUtil.authorizationValue(username, testPass))
//    ):
//    override def onText(message: String): Unit =
//      parse(message).fold(err => println(err), ok => onJson(ok))
