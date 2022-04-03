package com.malliina.logstreams.http4s

import cats.data.NonEmptyList
import cats.effect.IO
import com.malliina.app.AppMeta
import com.malliina.logstreams.Errors
import com.malliina.logstreams.auth.*
import com.malliina.logstreams.auth.AuthProvider.{Google, PromptKey, SelectAccount}
import com.malliina.logstreams.db.{DoobieDatabase, StreamsQuery}
import com.malliina.logstreams.html.Htmls
import com.malliina.logstreams.html.Htmls.{PasswordKey, UsernameKey}
import com.malliina.logstreams.http4s.BasicService.noCache
import com.malliina.logstreams.http4s.Service.log
import com.malliina.logstreams.http4s.UserRequest
import com.malliina.logstreams.models.AppName
import com.malliina.util.AppLogger
import com.malliina.values.{Email, Password, Username}
import com.malliina.web.*
import com.malliina.web.OAuthKeys.{Nonce, State}
import com.malliina.web.Utils.randomString
import io.circe.syntax.EncoderOps
import org.http4s.headers.{Location, `WWW-Authenticate`}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.{Callback as _, *}

import java.time.{Instant, OffsetDateTime, OffsetTime}

object Service:
  private val log = AppLogger(getClass)

  def apply(
    db: DoobieDatabase,
    users: UserService[IO],
    htmls: Htmls,
    auths: Auther,
    sockets: LogSockets,
    google: GoogleAuthFlow
  ): Service =
    new Service(db, users, htmls, auths, sockets, google)

class Service(
  val db: DoobieDatabase,
  val users: UserService[IO],
  htmls: Htmls,
  auths: Auther,
  sockets: LogSockets,
  google: GoogleAuthFlow
) extends BasicService[IO]:
  val reverse = LogRoutes

  def routes(socketBuilder: WebSocketBuilder2[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" => ok(AppMeta.ThisApp.asJson)
    case GET -> Root / "ping"   => ok(AppMeta.ThisApp.asJson)
    case req @ GET -> Root =>
      webAuth(req) { user =>
        users.all().flatMap { us => ok(htmls.logs(us.map(u => AppName(u.name))).tags) }
      }
    case req @ GET -> Root / "sources" =>
      webAuth(req) { src =>
        ok(htmls.sources.tags)
      }
    case req @ GET -> Root / "users" =>
      webAuth(req) { user =>
        users.all().flatMap { us => ok(htmls.users(us, None).tags) }
      }
    case req @ POST -> Root / "users" =>
      webAuth(req) { user =>
        req.decode[UrlForm] { form =>
          def read[T](key: String, build: String => T) =
            form.getFirst(key).map(build).toRight(Errors.single(s"Missing '$key'."))
          val maybeCreds = for
            username <- read(UsernameKey, Username.apply)
            password <- read(PasswordKey, Password.apply)
          yield com.malliina.logstreams.auth.BasicCredentials(username, password)
          maybeCreds.fold(
            err => unauthorized(err),
            newUser =>
              users.add(newUser).flatMap { e =>
                e.fold(
                  err =>
                    val msg = buildMessage(
                      user,
                      s"failed to add '${newUser.username}' because that user already exists."
                    )
                    log.error(msg)
                  ,
                  _ => ()
                )
                SeeOther(Location(reverse.allUsers))
              }
          )
        }
      }
    case req @ POST -> Root / "users" / UsernameVar(targetUser) / "delete" =>
      webAuth(req) { principal =>
        users.remove(targetUser).flatMap { res =>
          val feedback = res.fold(
            _ =>
              log.error(
                buildMessage(
                  principal,
                  s"failed to delete '$targetUser' because that user does not exist"
                )
              )
            //            UserFeedback.error(s"User '$user' does not exist.")
            ,
            _ => log.info(buildMessage(principal, s"deleted '$targetUser'."))
            //            UserFeedback.success(s"Deleted '$user'.")
          )
          SeeOther(Location(reverse.allUsers))
        }
      }
    case req @ GET -> Root / "ws" / "logs" =>
      webAuth(req) { principal =>
        val user = principal.user
        StreamsQuery
          .fromQuery(req.uri.query)
          .fold(
            err =>
              log.warn(s"Invalid log stream request by '$user'. $err")
              BadRequest(err.asJson)
            ,
            query =>
              log.info(s"Opening log stream at level ${query.level} for '$user'...")
              sockets.listener(query, socketBuilder)
          )
      }
    case req @ GET -> Root / "ws" / "admins" =>
      webAuth(req) { principal =>
        sockets.admin(principal, socketBuilder)
      }
    case req @ GET -> Root / "ws" / "sources" =>
      sourceAuth(req.headers) { src =>
        log.info(s"Connection authenticated for source '$src'.")
        sockets.source(
          UserRequest(src, req.headers, Urls.address(req), OffsetDateTime.now()),
          socketBuilder
        )
      }
    case req @ GET -> Root / "oauth" =>
      startHinted(Google, google, req)
    case req @ GET -> Root / "oauthcb" =>
      handleCallback(
        req,
        Google,
        cb => google.validateCallback(cb).map(e => e.flatMap(google.parse))
      )
  }
  val cookieNames = auths.web.cookieNames

  private def startHinted(
    provider: AuthProvider,
    validator: LoginHint[IO],
    req: Request[IO]
  ): IO[Response[IO]] = IO {
    val redirectUrl = Urls.hostOnly(req) / LogRoutes.googleCallback.renderString
    val lastIdCookie = req.cookies.find(_.name == cookieNames.lastId)
    val promptValue = req.cookies
      .find(_.name == cookieNames.prompt)
      .map(_.content)
      .orElse(Option(SelectAccount).filter(_ => lastIdCookie.isEmpty))
    val extra = promptValue.map(c => Map(PromptKey -> c)).getOrElse(Map.empty)
    val maybeEmail = lastIdCookie.map(_.content).filter(_ => extra.isEmpty)
    maybeEmail.foreach { hint =>
      log.info(s"Starting OAuth flow with $provider using login hint '$hint'...")
    }
    promptValue.foreach { prompt =>
      log.info(s"Starting OAuth flow with $provider using prompt '$prompt'...")
    }
    (redirectUrl, maybeEmail, extra)
  }.flatMap { case (redirectUrl, maybeEmail, extra) =>
    validator.startHinted(redirectUrl, maybeEmail, extra).flatMap { s =>
      startLoginFlow(s, req)
    }
  }

  private def startLoginFlow(s: Start, req: Request[IO]): IO[Response[IO]] = IO {
    val state = randomString()
    val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map { case (k, v) =>
      k -> Utils.urlEncode(v)
    }
    val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
    log.info(s"Redirecting to '$url' with state '$state'...")
    val sessionParams = Seq(State -> state) ++ s.nonce
      .map(n => Seq(Nonce -> n))
      .getOrElse(Nil)
    (url, sessionParams)
  }.flatMap { case (url, sessionParams) =>
    SeeOther(Location(Uri.unsafeFromString(url.url))).map { res =>
      val session = sessionParams.toMap.asJson
      auths.web
        .withSession(session, req, res)
        .putHeaders(noCache)
    }
  }

  private def handleCallback(
    req: Request[IO],
    provider: AuthProvider,
    validate: Callback => IO[Either[AuthError, Email]]
  ): IO[Response[IO]] =
    val params = req.uri.query.params
    val session = auths.web.session[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
    val cb = Callback(
      params.get(OAuthKeys.State),
      session.get(State),
      params.get(OAuthKeys.CodeKey),
      session.get(Nonce),
      Urls.hostOnly(req) / LogRoutes.googleCallback.renderString
    )
    validate(cb).flatMap { e =>
      e.fold(
        err => unauthorized(Errors(err.message)),
        email => userResult(email, provider, req)
      )
    }

  private def userResult(
    email: Email,
    provider: AuthProvider,
    req: Request[IO]
  ): IO[Response[IO]] =
    val returnUri: Uri = req.cookies
      .find(_.name == cookieNames.returnUri)
      .flatMap(c => Uri.fromString(c.content).toOption)
      .getOrElse(LogRoutes.index)
    SeeOther(Location(returnUri)).map { res =>
      auths.web.withAppUser(UserPayload.email(email), provider, req, res)
    }

  def stringify(map: Map[String, String]): String =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  def webAuth(req: Request[IO])(code: UserRequest => IO[Response[IO]]) =
    withAuth(auths.viewers, req.headers) { user =>
      code(UserRequest(user, req.headers, Urls.address(req), OffsetDateTime.now()))
    }

  def sourceAuth(headers: Headers)(code: Username => IO[Response[IO]]) =
    withAuth(auths.sources, headers)(code)

  private def withAuth(auth: Http4sAuthenticator[IO, Username], headers: Headers)(
    code: Username => IO[Response[IO]]
  ) =
    auth.authenticate(headers).flatMap { e => e.fold(err => onUnauthorized(err), ok => code(ok)) }

  def buildMessage(req: UserRequest, message: String) =
    s"User '${req.user}' from '${req.address}' $message."

  def onUnauthorized(error: IdentityError): IO[Response[IO]] =
    log.debug(s"Unauthorized. $error")
    unauthorized(Errors.single(s"Unauthorized."))

  def unauthorized(errors: Errors) = SeeOther(Location(LogRoutes.googleStart))

  def unauthorizedEnd(errors: Errors) =
    Unauthorized(
      `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
      errors.asJson
    ).map(r => auths.web.clearSession(r))
