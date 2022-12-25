package it

import cats.effect.unsafe.implicits.global
import cats.effect.{Deferred, IO, Resource}
import com.malliina.http.FullUrl
import com.malliina.http.io.SocketEvent.{Open, TextMessage}
import com.malliina.http.io.{HttpClientF2, HttpClientIO, SocketEvent, WebSocketF}
import com.malliina.logstreams.auth.BasicCredentials
import com.malliina.logstreams.client.{HttpUtil, KeyValue}
import com.malliina.logstreams.http4s.LogRoutes
import com.malliina.logstreams.models.*
import com.malliina.values.{Password, Username}
import com.malliina.util.AppLogger
import fs2.Stream
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

  http.test("can open socket".ignore) { client =>
    val c = creds("u1")
    users.add(c).flatMap { _ =>
      withSource(c.username.name, client) { socket =>
        IO(assertEquals(1, 1))
      }
    }
  }

  http.test("sent message is received by listener".ignore) { client =>
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

  http.test("admin receives status") { client =>
    withAdminEvents(client) { events =>
      events.take(1).compile.toList
    }.map { jsons =>
      assertEquals(jsons.length, 1)
      assert(jsons.head.as[LogSources].isRight)
    }
  }

  // 1. admin joins, no viewers 2. viewer joins 3. another admin joins 4. viewer disconnects
  http.test(
    "admin receives status on connect and updates when a source connects and disconnects"
  ) { client =>
    val user = "u3"
    Deferred[IO, Json].flatMap { status =>
      Deferred[IO, Json].flatMap { update =>
        Deferred[IO, Json].flatMap { disconnectedPromise =>
          components.users.add(creds(user)).flatMap { _ =>
            withAdminEvents(client) { jsons =>
              val listen = jsons
                .take(3)
                .evalMap { json =>
                  for
                    wasStatusEmpty <- status.complete(json)
                    wasUpdateEmpty <-
                      if wasStatusEmpty then IO.pure(false) else update.complete(json)
                    _ <-
                      if wasStatusEmpty || wasUpdateEmpty then IO.pure(false)
                      else disconnectedPromise.complete(json)
                  yield ()
                }
              val check =
                status.get.flatMap { statusJson =>
                  log.info(s"Status $statusJson")
                  val msg = statusJson.as[LogSources]
                  assert(msg.isRight)
                  assert(msg.toOption.get.sources.isEmpty)
                  val task = withSource(user, client) { _ =>
                    update.get.flatMap { updateJson =>
                      log.info(s"Update $updateJson")
                      val upd = updateJson.as[LogSources]
                      assert(upd.isRight)
                      val sources = upd.toOption.get.sources
                      assertEquals(sources.size, 1)
                      assertEquals(sources.head.name.name, user)
                      log.info("Admin client...")
                      val task22 = withAdminEvents(client) { jsons =>
                        jsons.take(1).compile.toList.map(_.head)
                      }
                      task22.map { adminStatusJson =>
                        log.info(s"Admin status $adminStatusJson")
                        val res = adminStatusJson.as[LogSources]
                        assert(res.isRight)
                        val statusUpdate = res.fold(err => throw err, identity)
                        assert(statusUpdate.sources.nonEmpty)
                      }
                    }
                  }
                  for
                    _ <- task
                    disconnected <- disconnectedPromise.get
                  yield
                    log.info(s"Disconnected $disconnected")
                    val disconnectUpdate = disconnected.as[LogSources]
                    assert(disconnectUpdate.isRight)
                    assert(disconnectUpdate.toOption.get.sources.isEmpty)
                }
              listen.concurrently(Stream.eval(check)).compile.drain
            }
          }
        }
      }
    }
  }

  def withAdmin[T](httpClient: HttpClientF2[IO])(code: WebSocketF[IO] => IO[T]) =
    openAuthedSocket(testUser, LogRoutes.sockets.admins, httpClient)(code)

  def withAdminEvents[T](httpClient: HttpClientF2[IO])(code: Stream[IO, Json] => IO[T]) =
    openAuthedSocketEvents(testUser, LogRoutes.sockets.admins, httpClient)(code)

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

  def openAuthedSocketEvents[T](
    username: String,
    uri: Uri,
    httpClient: HttpClientF2[IO]
  )(
    code: Stream[IO, Json] => IO[T]
  ): IO[T] =
    val wsUrl = FullUrl("ws", s"localhost:$port", uri.renderString)
    val kvs: List[KeyValue] = List(
      HttpUtil.Authorization -> HttpUtil.authorizationValue(username, testPass)
    )
    openSocket2(wsUrl, kvs, httpClient)(code)

  def openSocket[T](url: FullUrl, headers: List[KeyValue], httpClient: HttpClientF2[IO])(
    code: WebSocketF[IO] => IO[T]
  ): IO[T] =
    httpClient.socket(url, headers.map(kv => kv.key -> kv.value).toMap).use { socket =>
      val openEvents = socket.events.collect { case o @ Open(_, _) =>
        o
      }
      openEvents.take(1).compile.toList >> IO(log.info(s"Opened $url.")) >> code(socket)
    }

  def openSocket2[T](url: FullUrl, headers: List[KeyValue], httpClient: HttpClientF2[IO])(
    code: Stream[IO, Json] => IO[T]
  ): IO[T] =
    httpClient.socket(url, headers.map(kv => kv.key -> kv.value).toMap).use { socket =>
      val stream = socket.jsonMessages.concurrently(Stream.eval(socket.connectSocket))
      code(stream)
    }
