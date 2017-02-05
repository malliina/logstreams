package com.malliina.logstreams.client

import java.net.URI
import javax.net.ssl.SSLContext

import com.malliina.logbackrx.BasicPublishRxAppender
import rx.lang.scala.Subscription

/** Usage example in logback.xml under the <configuration> element:
  *
  * <appender name="LOGSTREAMS" class="com.malliina.logstreams.client.LogStreamsLogbackAppender">
  *   <host>localhost:9000</host>
  *   <username>${LOGSTREAMS_USER}</username>
  *   <password>${LOGSTREAMS_PASS}</password>
  * </appender>
  */
class LogStreamsLogbackAppender extends BasicPublishRxAppender {
  private var parsedHost: Option[String] = None
  private var username: Option[String] = None
  private var password: Option[String] = None
  private var client: Option[JsonSocket] = None
  private var subscription: Option[Subscription] = None
  private var secure: Boolean = true

  def getHost: String = parsedHost.orNull

  def setEndpoint(dest: String): Unit = parsedHost = Option(dest)

  def getUsername: String = username.orNull

  def setUsername(user: String): Unit = username = Option(user)

  def getPassword: String = password.orNull

  def setPassword(pass: String): Unit = password = Option(pass)

  def getSecure: Boolean = secure

  def setSecure(isSecure: Boolean) = secure = isSecure

  override def start() = {
    val result = for {
      h <- toMissing(parsedHost, "host")
      user <- toMissing(username, "username")
      pass <- toMissing(password, "password")
      _ <- validate(h).right
    } yield {
      val headers: Seq[KeyValue] = Seq(HttpUtil.basicAuth(user, pass))
      val sf = SSLContext.getDefault.getSocketFactory
      val scheme = if (secure) "wss" else "ws"
      val uri = new URI(s"$scheme://$h/ws/sources")
      addInfo(s"Connecting to logstreams URI ${uri.toString}...")
      val socket = new JsonSocket(uri, sf, headers)
      client = Option(socket)
      val sub = logEvents.subscribe(
        next => socket.sendMessage(LogEvents(Seq(next))),
        err => addError(s"Appender [$name] failed.", err),
        () => addError(s"Appender [$name] completed.")
      )
      subscription = Option(sub)
      super.start()
    }
    result.left.toOption foreach addError
  }

  def toMissing[T](o: Option[T], fieldName: String) = o.toRight(missing(fieldName)).right

  def validate(host: String): Either[String, Unit] =
    if (host contains "/") Left(s"Host $host must not contain a slash ('/'). Only supply the host (and optionally, port).")
    else Right(())

  def missing(fieldName: String) = s"No $fieldName is set for appender [$name]."

  override def stop() = {
    subscription.foreach(_.unsubscribe())
    subscription = None
    client.foreach(_.close())
    super.stop()
  }
}
