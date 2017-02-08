package com.malliina.logstreams.js

import scalatags.Text.all._

class SourceSocket extends SocketJS("/ws/admins?f=json") {
  val TableId = "sourceTable"
  val table = elem(s"$TableId tbody")

  override def handlePayload(payload: String): Unit = {
    val parsed = validate[LogSources](payload)
    parsed.fold(onInvalidData(payload).lift, onParsed)
  }

  def onParsed(data: LogSources) = {
    table.html(data.sources.map(toRow).render)
  }

  def toRow(source: LogSource) =
    tr(
      td(source.name),
      td(source.remoteAddress)
    )
}
