package com.malliina.logstreams.js

import com.malliina.logstreams.models.{LogSource, LogSources}
import play.api.libs.json.JsValue
import scalatags.Text
import scalatags.Text.all._

class SourcesSocket extends BaseSocket("/ws/admins?f=json") {
  val tableBody = e(SourceTableId).getElementsByTagName("tbody").head

  override def handlePayload(payload: JsValue): Unit =
    handleValidated[LogSources](payload)(onParsed)

  def onParsed(data: LogSources): Unit =
    tableBody.innerHTML = data.sources.map(toRow).render

  def toRow(source: LogSource): Text.TypedTag[String] =
    tr(
      td(source.name.name),
      td(source.remoteAddress)
    )
}
