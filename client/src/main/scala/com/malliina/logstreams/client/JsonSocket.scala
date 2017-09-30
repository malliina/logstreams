package com.malliina.logstreams.client

import javax.net.ssl.SSLSocketFactory

import com.malliina.http.FullUrl
import com.malliina.logstreams.client.JsonSocket.log
import play.api.libs.json.{JsValue, Json, Writes}

import scala.util.Try

object JsonSocket {
  private val log = Logging(getClass)
}

class JsonSocket(uri: FullUrl, socketFactory: SSLSocketFactory, headers: Seq[KeyValue])
  extends SocketClient(uri, socketFactory, headers) {

  def onMessage(message: JsValue): Unit = ()

  override def onText(message: String) = Try(Json.parse(message)).map(onMessage) recover {
    case t => log.error(s"Received non-JSON text: '$message'.", t)
  }

  def sendMessage[C: Writes](message: C) = send(Json.stringify(Json.toJson(message)))
}
