package com.malliina.logstreams.js

import com.malliina.logstreams.models.{LogSource, LogSources}
import io.circe.Json
import scalatags.Text
import scalatags.Text.all.*

class SourcesSocket extends BaseSocket("/ws/admins?f=json"):
  val tableBody = elem(SourceTableId).getElementsByTagName("tbody").head

  override def handlePayload(payload: Json): Unit =
    handleValidated[LogSources](payload)(onParsed)

  def onParsed(data: LogSources): Unit =
    tableBody.innerHTML = data.sources.map(toRow).render

  def toRow(source: LogSource): Text.TypedTag[String] =
    tr(
      td(source.name.name),
      td(source.remoteAddress),
      td(source.id),
      td(source.joinedFormatted)
    )
