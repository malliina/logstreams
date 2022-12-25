package com.malliina.logstreams.client

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.syntax.all.toFlatMapOps
import ch.qos.logback.classic.LoggerContext
import com.malliina.http.io.HttpClientF2
import com.malliina.logback.LogbackUtils
import com.malliina.values.ErrorMessage
import org.slf4j.LoggerFactory

object LogstreamsUtils:
  def install[F[_]: Async](
    user: String,
    userAgent: String,
    d: Dispatcher[F],
    http: HttpClientF2[F]
  ): F[Unit] =
    val lc = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val enabled = sys.env.get("LOGSTREAMS_ENABLED").contains("true")
    if enabled then
      FS2Appender
        .default(
          d,
          http,
          Map(HttpUtil.UserAgent -> userAgent)
        )
        .flatMap { appender =>
          env("LOGSTREAMS_PASS")
            .fold(
              err => Async[F].raiseError(IllegalArgumentException(err.message)),
              pass =>
                Async[F].delay {
                  appender.setContext(lc)
                  appender.setName("LOGSTREAMS")
                  appender.setEndpoint("wss://logs.malliina.com/ws/sources")
                  appender.setUsername(sys.env.getOrElse("LOGSTREAMS_USER", user))
                  appender.setPassword(pass)
                  appender.setEnabled(enabled)
                  LogbackUtils.installAppender(appender)
                }
            )
        }
    else Async[F].unit

  def env(key: String) =
    sys.env.get(key).toRight(ErrorMessage(s"No $key environment variable set."))
