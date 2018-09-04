package com.malliina.logstreams.client

import akka.stream.scaladsl.Sink
import com.malliina.http.FullUrl
import com.malliina.logback.akka.DefaultAkkaAppender
import com.malliina.logbackrx.LogEvent

import scala.concurrent.ExecutionContext

class LogstreamsLogbackAkkaAppender extends DefaultAkkaAppender {
  implicit val ec: ExecutionContext = m.executionContext
  private var endpoint: Option[String] = None
  private var username: Option[String] = None
  private var password: Option[String] = None
  private var enabled: Boolean = false
  private var client: Option[JsonSocket] = None
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

  override def start(): Unit = {
    if (getEnabled) {
      val result = for {
        hostAndPort <- toMissing(endpoint, "endpoint")
        user <- toMissing(username, "username")
        pass <- toMissing(password, "password")
        _ <- validate(hostAndPort).right
      } yield {
        val headers: Seq[KeyValue] = Seq(HttpUtil.basicAuth(user, pass))
        val host = hostAndPort.takeWhile(_ != ':')
        val sf = CustomSSLSocketFactory.forHost(host)
        val scheme = if (getSecure) "wss" else "ws"
        val uri = FullUrl(scheme, hostAndPort, "/ws/sources")
        addInfo(s"Connecting to logstreams URL '$uri' with Akka Streams for Logback...")
        val socket = new JsonSocket(uri, sf, headers)
        client = Option(socket)
        val socketSink = Sink.foreach[LogEvent] { event =>
          socket.sendMessage(LogEvents(Seq(event)))
        }
        val task = logEvents.runWith(socketSink)
        task.onComplete { t =>
          t.fold(err => addError(s"Appender [$name] failed.", err),
            _ => addError(s"Appender [$name] completed."))
        }
        super.start()
      }
      result.left.toOption foreach addError
    } else {
      addInfo("Logstreams client is disabled.")
    }
  }

  def toMissing[T](o: Option[T], fieldName: String) = o.toRight(missing(fieldName)).right

  def validate(host: String): Either[String, Unit] =
    if (host contains "/") Left(s"Host '$host' must not contain a slash ('/'). Only supply the host (and optionally, port).")
    else Right(())

  def missing(fieldName: String) = s"No '$fieldName' is set for appender [$name]."

  override def stop(): Unit = {
    closeImmediately()
    client.foreach(_.close())
    super.stop()
  }
}
