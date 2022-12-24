package com.malliina.logstreams.js

import com.malliina.http.FullUrl
import com.malliina.logstreams.js.BaseSocket.{EventKey, Ping}
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import org.scalajs.dom
import org.scalajs.dom.{CloseEvent, Event, MessageEvent}

import scala.util.Try

object BaseSocket:
  val EventKey = "event"
  val Ping = "ping"

class BaseSocket(wsPath: String, val log: BaseLogger = BaseLogger.printer) extends ScriptHelpers:
  private val statusElem = Option(elem("status"))
  val socket: dom.WebSocket = openSocket(wsPath)

  def handlePayload(payload: Json): Unit = ()

  def handleValidated[T: Decoder](json: Json)(process: T => Unit): Unit =
    json.as[T].fold(err => onJsonFailure(json, err), process)

  private def showConnected(): Unit =
    setFeedback("Connected.")

  private def showDisconnected(): Unit =
    setFeedback("Connection closed.")

  def send[T: Encoder](payload: T): Unit =
    val asString = payload.asJson.noSpaces
    socket.send(asString)

  private def onMessage(msg: MessageEvent): Unit =
    parse(msg.data.toString).fold(
      fail => onJsonException(fail),
      json =>
        val isPing = json.hcursor.downField(EventKey).as[String].exists(_ == Ping)
        if !isPing then
//          log.info(s"Handling json '$json'...")
          handlePayload(json)
    )

  private def onConnected(e: Event): Unit = showConnected()

  private def onClosed(e: CloseEvent): Unit = showDisconnected()

  def onError(e: Event): Unit = showDisconnected()

  private def openSocket(pathAndQuery: String) =
    val url = wsBaseUrl.append(pathAndQuery)
    val socket = new dom.WebSocket(url.url)
    socket.onopen = (e: Event) => onConnected(e)
    socket.onmessage = (e: MessageEvent) => onMessage(e)
    socket.onclose = (e: CloseEvent) => onClosed(e)
    socket.onerror = (e: Event) => onError(e)
    socket

  private def wsBaseUrl: FullUrl =
    val location = dom.window.location
    val wsProto = if location.protocol == "http:" then "ws" else "wss"
    FullUrl(wsProto, location.host, "")

  private def setFeedback(feedback: String): Unit = statusElem.foreach(_.innerHTML = feedback)

  private def onJsonException(t: ParsingFailure): Unit =
    log.error(t.underlying)

  private def onJsonFailure(value: Json, result: DecodingFailure): Unit =
    log.info(s"JSON error for '$value': '$result'.")

  def clear(): Unit =
    elem(TableHeadId).innerHTML = ""
    elem(TableBodyId).innerHTML = ""

  def close(): Unit = socket.close()
