package com.malliina.logstreams.http4s

import cats.effect.IO
import com.malliina.app.AppMeta
import com.malliina.logstreams.auth.{Auths, Http4sAuthenticator, UserService}
import com.malliina.logstreams.html.Htmls
import com.malliina.logstreams.models.AppName
import com.malliina.util.AppLogger
import controllers.UserRequest
import play.api.libs.json.Json
import org.http4s.{play, _}
import Service.log
import cats.data.NonEmptyList
import com.malliina.logstreams.Errors
import com.malliina.logstreams.db.StreamsQuery
import org.http4s.headers.{Location, `WWW-Authenticate`}
import com.malliina.values.{Password, Username}

object Service {
  private val log = AppLogger(getClass)

  def apply(users: UserService[IO], htmls: Htmls, auths: Auths, sockets: LogSockets): Service =
    new Service(users, htmls, auths, sockets)
}

class Service(users: UserService[IO], htmls: Htmls, auths: Auths, sockets: LogSockets)
  extends BasicService[IO] {
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
          } yield com.malliina.play.auth.BasicCredentials(username, password)
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
  }

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

  def unauthorized(errors: Errors) =
    Unauthorized(
      `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
      Json.toJson(errors)
    ).map(r => auths.web.clearSession(r))
}
