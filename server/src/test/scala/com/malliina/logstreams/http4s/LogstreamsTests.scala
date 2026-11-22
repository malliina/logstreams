package com.malliina.logstreams.http4s

import cats.effect.{Deferred, IO}
import com.malliina.http.SocketEvent.Open
import com.malliina.http.io.{HttpClientF2, OkSocket}
import com.malliina.http.{FullUrl, ReconnectingSocket, SocketEvent}
import com.malliina.logstreams.auth.BasicCredentials
import com.malliina.logstreams.client.{HttpUtil, KeyValue}
import com.malliina.logstreams.http4s.LogRoutes
import com.malliina.logstreams.http4s.LogstreamsTests.TestSocket
import com.malliina.logstreams.models.*
import com.malliina.util.AppLogger
import com.malliina.values.Literals.{pass, user}
import com.malliina.values.{Password, Username}
import fs2.Stream
import io.circe.Json
import org.http4s.Uri

object LogstreamsTests:
  val testUsername: Username = user"u"

  case class TestSocket(socket: ReconnectingSocket[IO, OkSocket[IO]]):
    def connect = socket.allEvents
      .collect:
        case Open(url) => url
      .take(1)
      .concurrently(Stream.eval(socket.connectSocket))
      .compile
      .lastOrError
    def jsons = socket.jsonMessages.concurrently(Stream.eval(socket.connectSocket))

class LogstreamsTests extends TestServerSuite:
  val log = AppLogger(getClass)

  val testUser = LogstreamsTests.testUsername
  val testPass: Password = pass"p"
  val testCreds = creds(testUser)

  def creds(u: Username) = BasicCredentials(u, testPass)
  def port = server().server.address.getPort
  def components = server().app
  def users = components.users

  http.test("can open socket".ignore): client =>
    val c = creds(user"u1")
    users
      .add(c)
      .flatMap: _ =>
        withSource(c.username, client): socket =>
          IO(assertEquals(1, 1))

  http.test("sent message is received by listener"): client =>
    val message = "hello, world"
    val testEvent = LogEvent(
      None,
      Option(System.currentTimeMillis()),
      Option("now"),
      message,
      getClass.getName.stripSuffix("$"),
      "this thread",
      LogLevel.Info,
      None
    )

    val user = user"u2"
    val task = withSource(user, client): source =>
      withListener(client): listener =>
        val received =
          listener.flatMap: json =>
            json
              .as[FrontEvent]
              .fold(
                f => Stream.raiseError[IO](f),
                {
                  case SimpleEvent(event)         => Stream.empty
                  case ale @ AppLogEvents(events) => Stream.emit(ale)
                  case MetaEvent(event, meta)     => Stream.empty
                }
              )
        val payload = LogEvents(List(testEvent))
        val sendMessage = source.connect >> source.socket
          .send(payload)
          .map: success =>
            assert(success)
        received
          .concurrently(Stream.eval(sendMessage))
          .take(1)
          .compile
          .lastOrError
          .map: receivedEvent =>
            val events = receivedEvent.events
            assertEquals(events.size, 1)
            val event = events.head
            assertEquals(event.source.name, AppName.fromUsername(user))
            assertEquals(event.event.message, message)
    for
      _ <- users.add(creds(user))
      _ <- users.add(testCreds)
      t <- task
    yield t

  http.test("admin receives status"): client =>
    withAdminEvents(client): events =>
      events.take(1).compile.lastOrError
    .map: json =>
      assert(json.as[LogSources].isRight)

  // 1. admin joins, no viewers 2. viewer joins 3. another admin joins 4. viewer disconnects
  http.test("admin receives status on connect and updates when a source connects and disconnects"):
    client =>
      val user = user"u3"
      Deferred[IO, Json].flatMap: status =>
        Deferred[IO, Json].flatMap: update =>
          Deferred[IO, Json].flatMap: disconnectedPromise =>
            components.users
              .add(creds(user))
              .flatMap: _ =>
                withAdminEvents(client): jsons =>
                  val listen = jsons
                    .take(3)
                    .evalMap: json =>
                      for
                        wasStatusEmpty <- status.complete(json)
                        wasUpdateEmpty <-
                          if wasStatusEmpty then IO.pure(false) else update.complete(json)
                        _ <-
                          if wasStatusEmpty || wasUpdateEmpty then IO.pure(false)
                          else disconnectedPromise.complete(json)
                      yield ()
                  val check =
                    status.get.flatMap: statusJson =>
                      val msg = statusJson.as[LogSources]
                      assert(msg.isRight)
                      assert(msg.toOption.get.sources.isEmpty)
                      val task = withSource(user, client): src =>
                        src.connect >> update.get.flatMap: updateJson =>
                          val upd = updateJson.as[LogSources]
                          assert(upd.isRight)
                          val sources = upd.toOption.get.sources
                          assertEquals(sources.size, 1)
                          assertEquals(sources.head.name.name, user.name)
                          val task22 = withAdminEvents(client): jsons =>
                            jsons.take(1).compile.lastOrError
                          task22.map: adminStatusJson =>
                            val res = adminStatusJson.as[LogSources]
                            assert(res.isRight)
                            val statusUpdate = res.fold(err => throw err, identity)
                            assert(statusUpdate.sources.nonEmpty)
                      for
                        _ <- task
                        disconnected <- disconnectedPromise.get
                      yield
                        val disconnectUpdate = disconnected.as[LogSources]
                        assert(disconnectUpdate.isRight)
                        assert(disconnectUpdate.toOption.get.sources.isEmpty)
                  listen.concurrently(Stream.eval(check)).compile.drain

  def withAdminEvents[T](httpClient: HttpClientF2[IO])(code: Stream[IO, Json] => IO[T]) =
    val uri = LogRoutes.sockets.admins
    val wsUrl = FullUrl("ws", s"localhost:$port", uri.renderString)
    val kvs: List[KeyValue] = List(
      HttpUtil.Authorization -> HttpUtil.authorizationValue(testUser.name, testPass.pass)
    )
    openSocket(wsUrl, kvs, httpClient)(s => code(s.jsons))

  def withListener[T](httpClient: HttpClientF2[IO])(code: Stream[IO, Json] => IO[T]) =
    openAuthedSocket(testUser, LogRoutes.sockets.logs, httpClient)(s => code(s.jsons))

  def withSource[T](username: Username, httpClient: HttpClientF2[IO])(code: TestSocket => IO[T]) =
    openAuthedSocket(username, LogRoutes.sockets.sources, httpClient)(code)

  def openAuthedSocket[T](
    username: Username,
    uri: Uri,
    httpClient: HttpClientF2[IO]
  )(
    code: TestSocket => IO[T]
  ): IO[T] =
    val wsUrl = FullUrl("ws", s"localhost:$port", uri.renderString)
    val kvs: List[KeyValue] = List(
      HttpUtil.Authorization -> HttpUtil.authorizationValue(username.name, testPass.pass)
    )
    openSocket(wsUrl, kvs, httpClient)(code)

  def openSocket[T](url: FullUrl, headers: List[KeyValue], httpClient: HttpClientF2[IO])(
    code: TestSocket => IO[T]
  ): IO[T] =
    httpClient
      .socket(url, headers.map(kv => kv.key -> kv.value).toMap)
      .use: socket =>
        code(TestSocket(socket))
