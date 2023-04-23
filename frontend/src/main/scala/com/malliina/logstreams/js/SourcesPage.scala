package com.malliina.logstreams.js

import com.malliina.logstreams.js.ScriptHelpers.{SourceTableId, elem}
import com.malliina.logstreams.models.{LogSource, LogSources}
import io.circe.Json
import scalatags.Text
import scalatags.Text.all.*

class SourcesPage extends BaseSocket("/ws/admins?f=json"):
  val tableBody = elem(SourceTableId).getElementsByTagName("tbody").head

  override def handlePayload(payload: Json): Unit =
    handleValidated[LogSources](payload)(onParsed)

  private def onParsed(data: LogSources): Unit =
    tableBody.innerHTML = data.sources.map(toRow).render

  private def toRow(source: LogSource): Text.TypedTag[String] =
    tr(
      td(source.name.name),
      td(source.remoteAddress),
      td(source.userAgent.getOrElse("Unknown")),
      td(source.id),
      td(source.timeJoined)
    )
