package com.malliina.logstreams.http4s

import cats.effect.IO
import com.malliina.app.AppMeta
import com.malliina.logstreams.auth.{
  AuthProvider,
  Auths,
  Http4sAuthenticator,
  UserPayload,
  UserService
}
import com.malliina.logstreams.html.Htmls
import com.malliina.logstreams.models.AppName
import com.malliina.util.AppLogger
import controllers.UserRequest
import play.api.libs.json.Json
import org.http4s.{play, _}
import Service.log
import cats.data.NonEmptyList
import com.malliina.logstreams.Errors
import com.malliina.logstreams.auth.AuthProvider.{Google, PromptKey, SelectAccount}
import com.malliina.logstreams.db.StreamsQuery
import com.malliina.logstreams.http4s.BasicService.noCache
import org.http4s.headers.{Location, `WWW-Authenticate`}
import com.malliina.values.{Email, Password, Username}
import com.malliina.web.OAuthKeys.{Nonce, State}
import com.malliina.web.Utils.randomString
import com.malliina.web.{AuthError, Callback, GoogleAuthFlow, LoginHint, OAuthKeys, Start, Utils}

object Service {
  private val log = AppLogger(getClass)

  def apply(
    users: UserService[IO],
    htmls: Htmls,
    auths: Auths,
    sockets: LogSockets,
    google: GoogleAuthFlow
  ): Service =
    new Service(users, htmls, auths, sockets, google)
}

class Service(
  users: UserService[IO],
  htmls: Htmls,
  auths: Auths,
  sockets: LogSockets,
  google: GoogleAuthFlow
) extends BasicService[IO] {
  val reverse = LogRoutes
  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "health" => ok(Json.toJson(AppMeta.ThisApp))
    case GET -> Root / "ping"   => ok(Json.toJson(AppMeta.ThisApp))
    case GET -> Root =>
      users.all().flatMap { us => ok(htmls.logs(us.map(u => AppName(u.name))).tags) }
    case req @ GET -> Root / "sources" =>
      webAuth(req.headers) { src =>
        ok(htmls.sources.tags)
      }
    case req @ POST -> Root / "users" =>
      webAuth(req.headers) { user =>
        req.decode[UrlForm] { form =>
          def read[T](key: String, build: String => T) =
            form.getFirst(key).map(build).toRight(Errors.single(s"Missing '$key'."))
          val maybeCreds = for {
            username <- read("username", Username.apply)
            password <- read("password", Password.apply)
          } yield com.malliina.logstreams.auth.BasicCredentials(username, password)
          maybeCreds.fold(
            err => unauthorized(err),
            newUser =>
              users.add(newUser).flatMap { e =>
                e.fold(
                  err => {
                    val msg = buildMessage(
                      user,
                      s"failed to add '${newUser.username}' because that user already exists."
                    )
                    log.error(msg)
                  },
                  _ => ()
                )
                SeeOther(Location(reverse.allUsers))
              }
          )
        }
      }
    case req @ POST -> Root / "users" / UsernameVar(targetUser) / "delete" =>
      webAuth(req.headers) { principal =>
        users.remove(targetUser).flatMap { res =>
          val feedback = res.fold(
            _ => {
              log.error(
                buildMessage(
                  principal,
                  s"failed to delete '$targetUser' because that user does not exist"
                )
              )
              //            UserFeedback.error(s"User '$user' does not exist.")
            },
            _ => {
              log.info(buildMessage(principal, s"deleted '$targetUser'."))
              //            UserFeedback.success(s"Deleted '$user'.")
            }
          )
          SeeOther(Location(reverse.allUsers))
        }
      }
    case req @ POST -> Root / "ws" / "logs" =>
      webAuth(req.headers) { principal =>
        StreamsQuery
          .fromQuery(req.uri.query)
          .fold(
            err => BadRequest(Json.toJson(err)),
            ok => sockets.listener(ok)
          )
      }
    case req @ POST -> Root / "ws" / "admins" =>
      webAuth(req.headers) { principal =>
        sockets.admin(principal)
      }
    case req @ POST -> Root / "ws" / "sources" =>
      sourceAuth(req.headers) { src =>
        sockets.source(UserRequest(src, req.headers))
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
      .find(_.name == auths.web.cookieNames.prompt)
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
  }.flatMap {
    case (redirectUrl, maybeEmail, extra) =>
      validator.startHinted(redirectUrl, maybeEmail, extra).flatMap { s =>
        startLoginFlow(s, Urls.isSecure(req))
      }
  }

  private def startLoginFlow(s: Start, isSecure: Boolean): IO[Response[IO]] = IO {
    val state = randomString()
    val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map {
      case (k, v) =>
        k -> Utils.urlEncode(v)
    }
    val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
    log.info(s"Redirecting to '$url' with state '$state'...")
    val sessionParams = Seq(State -> state) ++ s.nonce
      .map(n => Seq(Nonce -> n))
      .getOrElse(Nil)
    (url, sessionParams)
  }.flatMap {
    case (url, sessionParams) =>
      SeeOther(Location(Uri.unsafeFromString(url.url))).map { res =>
        val session = Json.toJsObject(sessionParams.toMap)
        auths.web
          .withSession(session, isSecure, res)
          .putHeaders(noCache)
      }
  }

  private def handleCallback(
    req: Request[IO],
    provider: AuthProvider,
    validate: Callback => IO[Either[AuthError, Email]]
  ): IO[Response[IO]] = {
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
  }

  private def userResult(
    email: Email,
    provider: AuthProvider,
    req: Request[IO]
  ): IO[Response[IO]] = {
    val returnUri: Uri = req.cookies
      .find(_.name == cookieNames.returnUri)
      .flatMap(c => Uri.fromString(c.content).toOption)
      .getOrElse(LogRoutes.index)
    SeeOther(Location(returnUri)).map { r =>
      auths.web.withAppUser(UserPayload.email(email), Urls.isSecure(req), provider, r)
    }
  }

  def stringify(map: Map[String, String]): String =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  def webAuth(headers: Headers)(code: UserRequest => IO[Response[IO]]) =
    withAuth(auths.viewers, headers)(user => code(UserRequest(user, headers)))

  def sourceAuth(headers: Headers)(code: Username => IO[Response[IO]]) =
    withAuth(auths.sources, headers)(code)

  private def withAuth(auth: Http4sAuthenticator[IO, Username], headers: Headers)(
    code: Username => IO[Response[IO]]
  ) =
    auth.authenticate(headers).flatMap { e => e.fold(err => onUnauthorized(err), ok => code(ok)) }

  def buildMessage(req: UserRequest, message: String) =
    s"User '${req.user}' from '${req.address}' $message."

  def onUnauthorized(error: IdentityError): IO[Response[IO]] = {
    log.warn("Unauthorized.")
    unauthorized(Errors.single(s"Unauthorized."))
  }

  def unauthorized(errors: Errors) = SeeOther(Location(LogRoutes.googleStart))

  def unauthorizedEnd(errors: Errors) =
    Unauthorized(
      `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
      Json.toJson(errors)
    ).map(r => auths.web.clearSession(r))
}
