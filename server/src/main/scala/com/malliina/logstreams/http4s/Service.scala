package com.malliina.logstreams.http4s

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.effect.kernel.Async
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.app.AppMeta
import com.malliina.database.DoobieDatabase
import com.malliina.http.Errors
import com.malliina.http4s.BasicService.noCache
import com.malliina.http4s.{CSRFSupport, FormDecoders, FormReadable, FormReadableT}
import com.malliina.logstreams.auth.*
import com.malliina.logstreams.auth.AuthProvider.{Google, PromptKey, SelectAccount}
import com.malliina.logstreams.db.StreamsQuery
import com.malliina.logstreams.html.Htmls
import com.malliina.logstreams.html.Htmls.{PasswordKey, UsernameKey}
import com.malliina.logstreams.http4s.Service.{log, given}
import com.malliina.logstreams.models.{AppName, LogClientId}
import com.malliina.util.AppLogger
import com.malliina.values.{Email, Password, Username}
import com.malliina.web.*
import com.malliina.web.OAuthKeys.{Nonce, State}
import com.malliina.web.Utils.randomString
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.server.middleware.CSRF
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.{BasicCredentials as _, Callback as _, *}

import scala.concurrent.duration.DurationInt

object Service:
  private val log = AppLogger(getClass)

  given FormReadableT[BasicCredentials] = FormReadableT.reader.emap: form =>
    for
      username <- form
        .read[Username](UsernameKey)
        .filterOrElse(_.name.nonEmpty, Errors("Username was empty."))
      password <- form
        .read[Password](PasswordKey)
        .filterOrElse(_.pass.nonEmpty, Errors("Password was empty."))
    yield BasicCredentials(username, password)

class Service[F[_]: Async](
  val db: DoobieDatabase[F],
  val users: UserService[F],
  htmls: Htmls,
  auths: Auther[F],
  sockets: LogSockets[F],
  google: GoogleAuthFlow[F],
  val csrf: CSRF[F, F]
) extends LogsService[F]
  with FormDecoders[F]
  with CSRFSupport[F]:
  val reverse = LogRoutes
  val F = Sync[F]

  def routes(socketBuilder: WebSocketBuilder2[F]): HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "health" => ok(AppMeta.ThisApp)
    case GET -> Root / "ping"   => ok(AppMeta.ThisApp)
    case req @ GET -> Root      =>
      logsRequest(req): (query, user) =>
        seeOther(reverse.toLogs(StreamsQuery.toQuery(query)))
    case req @ GET -> Root / "logs" =>
      logsRequest(req): (query, user) =>
        users
          .all()
          .flatMap: us =>
            ok(htmls.logs(us.map(u => AppName.fromUsername(u)), query))
    case req @ GET -> Root / "sources" =>
      webAuth(req): src =>
        ok(htmls.sources)
    case req @ GET -> Root / "sources" / "token" =>
      val info = SocketInfo(AppName.unsafe("app"), LogClientId.random())
      val signed = auths.web.jwt.sign(info, ttl = 1.hour)
      ok(TokenResponse(signed))
    case req @ POST -> Root / "sources" / "token" =>
      req
        .decodeJson[TokenRequest]
        .flatMap: body =>
          val app = body.app
          val suffixes = Seq("ios", "android", "web")
          val legalApp = suffixes.exists(s => app.name.endsWith(s))
          if legalApp then
            val signed = auths.web.jwt.sign(SocketInfo(app, LogClientId.random()), ttl = 1.hour)
            ok(TokenResponse(signed))
          else unauthorized(Errors.single(s"Illegal app: '$app'."))
    case req @ GET -> Root / "users" =>
      webAuth(req): user =>
        val feedback = req.feedbackAs[UserFeedback]
        users
          .all()
          .flatMap: us =>
            csrfOk: token =>
              htmls.users(us, feedback, token)
          .clearFeedback
    case req @ POST -> Root / "users" =>
      webAuth(req): user =>
        req
          .attemptAs[BasicCredentials]
          .foldF(
            fail =>
              val message = fail match
                case MalformedMessageBodyFailure(details, _) => details
                case _                                       => "Form failure."
              log.error(s"Form failure. $fail")
              seeOther(reverse.allUsers).withFeedback(UserFeedback.error(message))
            ,
            newUser =>
              users
                .add(newUser)
                .flatMap: e =>
                  val message = e.fold(
                    err =>
                      val msg = buildMessage(
                        user,
                        s"failed to add '${newUser.username}' because that user already exists"
                      )
                      log.error(msg)
                      UserFeedback.error(s"User '${newUser.username}' already exists.")
                    ,
                    _ =>
                      val msg = buildMessage(user, s"added '${newUser.username}'")
                      log.info(msg)
                      UserFeedback.success(s"Added user '${newUser.username}'.")
                  )
                  seeOther(reverse.allUsers).withFeedback(message)
          )
    case req @ POST -> Root / "users" / UsernameVar(targetUser) / "delete" =>
      webAuth(req): principal =>
        users
          .remove(targetUser)
          .flatMap: res =>
            val feedback: Unit = res.fold(
              _ =>
                log.error(
                  buildMessage(
                    principal,
                    s"failed to delete '$targetUser' because that user does not exist"
                  )
                )
              //            UserFeedback.error(s"User '$user' does not exist.")
              ,
              _ => log.info(buildMessage(principal, s"deleted '$targetUser'"))
              //            UserFeedback.success(s"Deleted '$user'.")
            )
            seeOther(reverse.allUsers).withFeedback(
              UserFeedback.success(s"Deleted user '$targetUser'.")
            )
    case req @ GET -> Root / "ws" / "logs" =>
      logsRequest(req): (query, user) =>
        log.info(s"Opening log stream with ${query.describe} for '$user'...")
        sockets.listener(query, socketBuilder)
    case req @ GET -> Root / "logs" / "history" =>
      logsRequest(req): (query, user) =>
        log.info(
          s"Searching for logs with ${query.describe} for '$user'..."
        )
        sockets.db
          .events(query)
          .flatMap: events =>
            ok(events)
    case req @ GET -> Root / "ws" / "admins" =>
      webAuth(req): principal =>
        sockets.admin(principal, socketBuilder)
    case req @ GET -> Root / "ws" / "sources" =>
      sourceAuth(req): src =>
        log.info(s"Connection authenticated for source '$src'.")
        sockets.source(UserRequest.req(src, req), socketBuilder)
    case req @ GET -> Root / "ws" / "sources" / "clients" =>
      publicAuth(req): src =>
        log.info(s"Opening connection for app '${src.describe}'.")
        sockets.source(
          UserRequest.make(Username.unsafe(src.app.name), req, Option(src.clientId)),
          socketBuilder
        )
    case req @ GET -> Root / "oauth" =>
      startHinted(Google, google, req)
    case req @ GET -> Root / "oauthcb" =>
      handleCallback(
        req,
        Google,
        cb => google.validateCallback(cb).map(e => e.flatMap(google.parse))
      )
  }

  private def logsRequest(req: Request[F])(q: (StreamsQuery, Username) => F[Response[F]]) =
    webAuth(req): principal =>
      val user = principal.user
      StreamsQuery
        .fromQuery(req.uri.query, principal.now.toInstant)
        .fold(
          err =>
            log.warn(s"Invalid log stream request by '$user'. $err")
            badRequest(err)
          ,
          query => q(query, user)
        )

  val cookieNames = auths.web.cookieNames

  private def startHinted(
    provider: AuthProvider,
    validator: LoginHint[F],
    req: Request[F]
  ): F[Response[F]] = F
    .delay:
      val redirectUrl = Urls.hostOnly(req) / LogRoutes.googleCallback.renderString
      val lastIdCookie = req.cookies.find(_.name == cookieNames.lastId)
      val promptValue = req.cookies
        .find(_.name == cookieNames.prompt)
        .map(_.content)
        .orElse(Option(SelectAccount).filter(_ => lastIdCookie.isEmpty))
      val extra = promptValue.map(c => Map(PromptKey -> c)).getOrElse(Map.empty)
      val maybeEmail = lastIdCookie.map(_.content).filter(_ => extra.isEmpty)
      maybeEmail.foreach: hint =>
        log.info(s"Starting OAuth flow with $provider using login hint '$hint'...")
      promptValue.foreach: prompt =>
        log.info(s"Starting OAuth flow with $provider using prompt '$prompt'...")
      (redirectUrl, maybeEmail, extra)
    .flatMap:
      case (redirectUrl, maybeEmail, extra) =>
        validator
          .startHinted(redirectUrl, maybeEmail, extra)
          .flatMap: s =>
            startLoginFlow(s, req)

  private def startLoginFlow(s: Start, req: Request[F]): F[Response[F]] = F
    .delay:
      val state = randomString()
      val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map: (k, v) =>
        k -> Utils.urlEncode(v)
      val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
      log.info(s"Redirecting to '$url' with state '$state'...")
      val sessionParams = Seq(State -> state) ++ s.nonce
        .map(n => Seq(Nonce -> n))
        .getOrElse(Nil)
      (url, sessionParams)
    .flatMap: (url, sessionParams) =>
      seeOther(Uri.unsafeFromString(url.url)).map: res =>
        val session = sessionParams.toMap.asJson
        auths.web
          .withSession(session, req, res)
          .putHeaders(noCache)

  private def handleCallback(
    req: Request[F],
    provider: AuthProvider,
    validate: Callback => F[Either[AuthError, Email]]
  ): F[Response[F]] =
    val params = req.uri.query.params
    val session = auths.web.session[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
    val cb = Callback(
      params.get(OAuthKeys.State),
      session.get(State),
      params.get(OAuthKeys.CodeKey),
      session.get(Nonce),
      Urls.hostOnly(req) / LogRoutes.googleCallback.renderString
    )
    validate(cb).flatMap: e =>
      e.fold(
        err => unauthorized(Errors(err.message)),
        email => userResult(email, provider, req)
      )

  private def userResult(
    email: Email,
    provider: AuthProvider,
    req: Request[F]
  ): F[Response[F]] =
    val returnUri: Uri = req.cookies
      .find(_.name == cookieNames.returnUri)
      .flatMap(c => Uri.fromString(c.content).toOption)
      .getOrElse(LogRoutes.index)
    seeOther(returnUri).map: res =>
      auths.web.withAppUser(UserPayload.email(email), provider, req, res)

  private def stringify(map: Map[String, String]): String =
    map.map((key, value) => s"$key=$value").mkString("&")

  private def webAuth(req: Request[F])(code: UserRequest => F[Response[F]]) =
    auths.viewers
      .authenticate(req.headers)
      .flatMap: e =>
        e.map: user =>
          code(UserRequest.req(user, req))
        .recover: err =>
            log.debug(s"Unauthorized. $err")
            unauthorized(Errors.single(s"Unauthorized."))

  private def sourceAuth(req: Request[F])(code: Username => F[Response[F]]) =
    authed(req, auths.sources): user =>
      code(user)

  private def publicAuth(req: Request[F])(code: SocketInfo => F[Response[F]]) =
    authed(req, auths.public): socket =>
      code(socket)

  private def authed[U, R](req: Request[F], auther: RequestAuthenticator[F, U])(
    code: U => F[Response[F]]
  ) =
    auther
      .authenticate(req)
      .flatMap: e =>
        e.map: socket =>
          code(socket)
        .recover: err =>
            log.warn(s"Unauthorized request to ${req.uri}. $err")
            unauthorizedJson()

  private def buildMessage(req: UserRequest, message: String) =
    s"User '${req.user}' from '${req.address}' $message."

  private def unauthorized(errors: Errors) = seeOther(LogRoutes.googleStart)

  private def unauthorizedJson(message: String = "Unauthorized."): F[Response[F]] =
    Unauthorized(
      `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
      Errors.single(message)
    ).map(_.putHeaders(noCache))
