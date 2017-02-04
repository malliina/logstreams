package com.malliina.logstreams.client

import java.net.URI

import com.malliina.logbackrx.BasicPublishRxAppender
import rx.lang.scala.Subscription

class LogStreamsLogbackAppender extends BasicPublishRxAppender {
  private var username: Option[String] = None
  private var password: Option[String] = None
  private var client: Option[JsonSocket] = None
  private var subscription: Option[Subscription] = None

  def getEndpoint: String = client.map(_.uri.toString).orNull

  def setEndpoint(dest: String): Unit = {
    val headers: Seq[KeyValue] = Seq(HttpUtil.basicAuth(getUsername, getPassword))
    val uri = new URI(dest)
    val sf = SSLUtils.trustAllSslContext().getSocketFactory
    client = Option(new JsonSocket(uri, sf, headers))
  }

  def getUsername: String = username.orNull

  def setUsername(user: String): Unit = username = Option(user)

  def getPassword: String = password.orNull

  def setPassword(pass: String): Unit = password = Option(pass)

  override def start() = {
    if (client.isEmpty) {
      missingField("endpoint")
    } else if (username.isEmpty) {
      missingField("username")
    } else if (password.isEmpty) {
      missingField("password")
    } else {
      val sub = logEvents.subscribe(
        next => client.foreach(_.sendMessage(LogEvents(Seq(next)))),
        err => addError(s"Appender [$name] failed.", err),
        () => addError(s"Appender [$name] completed.")
      )
      subscription = Option(sub)
      super.start()
    }
  }

  def missingField(fieldName: String) =
    addError(s"No $fieldName is set for appender [$name].")

  override def stop() = {
    subscription.foreach(_.unsubscribe())
    subscription = None
    super.stop()
  }
}
