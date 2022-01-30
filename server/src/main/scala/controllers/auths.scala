package controllers

import cats.effect.IO
import com.malliina.http.OkClient
import com.malliina.logstreams.http4s.{IdentityError, LogRoutes, Urls}
import com.malliina.util.AppLogger
import com.malliina.values.{Email, Username}
import com.malliina.web.AuthConf

import java.time.{Instant, OffsetDateTime}
import org.http4s.{Headers, Request}

import scala.concurrent.duration.{Duration, DurationInt}

trait LogAuth[F[_]]:
  def authenticateSocket(req: Request[IO]): F[Either[IdentityError, UserRequest]]

case class UserRequest(user: Username, headers: Headers, address: String, now: OffsetDateTime)
