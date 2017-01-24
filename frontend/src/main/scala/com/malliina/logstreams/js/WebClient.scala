package com.malliina.logstreams.js

import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLTableElement, HTMLTableRowElement}

class WebClient(wsPath: String) extends SocketJS(wsPath) {
  lazy val table = dom.document.getElementById("logTable").asInstanceOf[HTMLTableElement]

  override def handlePayload(payload: String): Unit = {
    setFeedback(payload)
    val row = table.insertRow(1).asInstanceOf[HTMLTableRowElement]
    val cell = row.insertCell(0)
    cell.innerHTML = payload
  }
}

object WebClient {
  def apply(wsPath: String) = new WebClient(s"$wsPath?f=json")

  def web = WebClient("/ws/clients")

  def server = WebClient("/ws/sources")

  @deprecated
  def rx = WebClient("/rx")
}
