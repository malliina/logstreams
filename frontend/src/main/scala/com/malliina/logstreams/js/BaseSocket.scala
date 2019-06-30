package com.malliina.logstreams.js

import com.malliina.http.FullUrl
import com.malliina.logstreams.js.BaseSocket.{EventKey, Ping}
import org.scalajs.dom
import org.scalajs.dom.CloseEvent
import org.scalajs.dom.raw.{Event, MessageEvent}
import play.api.libs.json._

import scala.util.Try

object BaseSocket {
  val EventKey = "event"
  val Ping = "ping"
}

class BaseSocket(wsPath: String, val log: BaseLogger = BaseLogger.noop) extends ScriptHelpers {
  val statusElem = Option(e("status"))
  val socket: dom.WebSocket = openSocket(wsPath)

  def handlePayload(payload: JsValue): Unit = ()

  def handleValidated[T: Reads](json: JsValue)(process: T => Unit): Unit = {
    json.validate[T].fold(err => onJsonFailure(json, JsError(err)), process)
  }

  def showConnected(): Unit = {
    setFeedback("Connected.")
  }

  def showDisconnected(): Unit = {
    setFeedback("Connection closed.")
  }

  def send[T: Writes](payload: T): Unit = {
    val asString = Json.stringify(Json.toJson(payload))
    socket.send(asString)
  }

  def onMessage(msg: MessageEvent): Unit = {
    Try(Json.parse(msg.data.toString)).map { json =>
      val isPing = (json \ EventKey).validate[String].filter(_ == Ping).isSuccess
      if (!isPing) {
        handlePayload(json)
      }
    }.recover { case e => onJsonException(e) }
  }

  def onConnected(e: Event): Unit = showConnected()

  def onClosed(e: CloseEvent): Unit = showDisconnected()

  def onError(e: Event): Unit = showDisconnected()

  def openSocket(pathAndQuery: String) = {
    val url = wsBaseUrl.append(pathAndQuery)
    val socket = new dom.WebSocket(url.url)
    socket.onopen = (e: Event) => onConnected(e)
    socket.onmessage = (e: MessageEvent) => onMessage(e)
    socket.onclose = (e: CloseEvent) => onClosed(e)
    socket.onerror = (e: Event) => onError(e)
    socket
  }

  def wsBaseUrl: FullUrl = {
    val location = dom.window.location
    val wsProto = if (location.protocol == "http:") "ws" else "wss"
    FullUrl(wsProto, location.host, "")
  }

  def setFeedback(feedback: String): Unit = statusElem.foreach(_.innerHTML = feedback)

  def onJsonException(t: Throwable): Unit = {
    log error t
  }

  protected def onJsonFailure(value: JsValue, result: JsError): Unit = {
    log info s"JSON error for '$value': '$result'."
  }

  def clear(): Unit = {
    e(TableHeadId).innerHTML = ""
    e(TableBodyId).innerHTML = ""
  }

  def close(): Unit = socket.close()
}
