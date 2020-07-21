package com.malliina.logstreams.client

import java.io.Closeable

import com.malliina.logback.akka.DefaultAkkaAppender

import scala.concurrent.ExecutionContext

class SocketAppender[T <: Closeable] extends DefaultAkkaAppender {
  implicit val ec: ExecutionContext = mat.executionContext
  var endpoint: Option[String] = None
  var username: Option[String] = None
  var password: Option[String] = None
  var client: Option[T] = None
  private var enabled: Boolean = false
  private var secure: Boolean = true

  def getEndpoint: String = endpoint.orNull

  def setEndpoint(dest: String): Unit = {
    addInfo(s"Setting endpoint '$dest' for appender [$name].")
    endpoint = Option(dest)
  }

  def getUsername: String = username.orNull

  def setUsername(user: String): Unit = {
    addInfo(s"Setting username '$user' for appender [$name].")
    username = Option(user)
  }

  def getPassword: String = password.orNull

  def setPassword(pass: String): Unit = {
    addInfo(s"Setting password for appender [$name].")
    password = Option(pass)
  }

  def getSecure: Boolean = secure

  def setSecure(isSecure: Boolean): Unit = {
    addInfo(s"Setting secure '$isSecure' for appender [$name].")
    secure = isSecure
  }

  def getEnabled: Boolean = enabled

  def setEnabled(isEnabled: Boolean): Unit = {
    addInfo(s"Setting enabled '$isEnabled' for appender [$name].")
    enabled = isEnabled
  }

  def toMissing[O](o: Option[O], fieldName: String) = o.toRight(missing(fieldName))

  def validate(host: String): Either[String, Unit] =
    if (host contains "/")
      Left(
        s"Host '$host' must not contain a slash ('/'). Only supply the host (and optionally, port)."
      )
    else Right(())

  def missing(fieldName: String) = s"No '$fieldName' is set for appender [$name]."

  override def stop(): Unit = {
    closeImmediately()
    client.foreach(_.close())
    super.stop()
  }
}
