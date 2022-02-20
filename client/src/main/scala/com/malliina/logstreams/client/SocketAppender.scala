package com.malliina.logstreams.client

import cats.effect.IO
import cats.effect.std.Dispatcher
import ch.qos.logback.classic.spi.ILoggingEvent
import com.malliina.http.FullUrl
import com.malliina.logback.fs2.{DefaultFS2IOAppender, FS2AppenderComps}
import fs2.concurrent.{SignallingRef, Topic}
import com.malliina.logback.fs2.LoggingComps
import java.io.Closeable

class SocketAppender[T](comps: LoggingComps) extends DefaultFS2IOAppender(comps):
  var endpoint: Option[FullUrl] = None
  var username: Option[String] = None
  var password: Option[String] = None
  var client: Option[T] = None
  private var enabled: Boolean = false
  private var secure: Boolean = true

  def getEndpoint: String = endpoint.map(_.url).orNull

  def setEndpoint(dest: String): Unit =
    FullUrl
      .build(dest)
      .fold(
        err => addError(err.message),
        url =>
          addInfo(s"Setting endpoint '$url' for appender [$name].")
          endpoint = Option(url)
      )

  def getUsername: String = username.orNull

  def setUsername(user: String): Unit =
    addInfo(s"Setting username '$user' for appender [$name].")
    username = Option(user)

  def getPassword: String = password.orNull

  def setPassword(pass: String): Unit =
    addInfo(s"Setting password for appender [$name].")
    password = Option(pass)

  def getSecure: Boolean = secure

  def setSecure(isSecure: Boolean): Unit =
    addInfo(s"Setting secure '$isSecure' for appender [$name].")
    secure = isSecure

  def getEnabled: Boolean = enabled

  def setEnabled(isEnabled: Boolean): Unit =
    addInfo(s"Setting enabled '$isEnabled' for appender [$name].")
    enabled = isEnabled

  def toMissing[O](o: Option[O], fieldName: String) = o.toRight(missing(fieldName))

  def missing(fieldName: String) = s"No '$fieldName' is set for appender [$name]."

  override def stop(): Unit =
//    client.foreach(_.close())
    super.stop()
