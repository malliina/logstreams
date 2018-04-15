package com.malliina.logstreams.js

import com.malliina.logstreams.models.{LogSource, LogSources}
import play.api.libs.json.JsValue
import scalatags.Text.all._

class SourcesSocket extends BaseSocket("/ws/admins?f=json") {
  val TableId = "source-table"
  val table = elem(s"$TableId tbody")

  override def handlePayload(payload: JsValue): Unit =
    handleValidated[LogSources](payload)(onParsed)

  def onParsed(data: LogSources) =
    table.html(data.sources.map(toRow).render)

  def toRow(source: LogSource) =
    tr(
      td(source.name.name),
      td(source.remoteAddress)
    )
}
