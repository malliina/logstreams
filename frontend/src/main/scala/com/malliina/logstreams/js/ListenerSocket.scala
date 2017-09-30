package com.malliina.logstreams.js

import java.util.UUID

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLTableElement
import org.scalajs.jquery.{JQuery, JQueryEventObject}
import play.api.libs.json.JsValue

import scalatags.Text.TypedTag
import scalatags.Text.all._

case class RowContent(content: Frag, cellId: String, linkId: String)

object ListenerSocket {
  def apply(wsPath: String) = new ListenerSocket(s"$wsPath?f=json")

  def web = ListenerSocket("/ws/logs")
}

class ListenerSocket(wsPath: String) extends BaseSocket(wsPath) {
  val CellContent = "cell-content"
  val CellWide = "cell-wide"
  val ColumnCount = 6
  val Danger = "danger"
  val Hidden = "hidden"
  val NoWrap = "no-wrap"
  val TableId = "log-table"
  val Warning = "warning"

  lazy val jQueryTable = elem(TableId)
  lazy val table = dom.document.getElementById(TableId).asInstanceOf[HTMLTableElement]

  override def handlePayload(payload: JsValue): Unit =
    handleValidated(payload)(onLogEvents)

  def onLogEvents(appLogEvents: AppLogEvents): Unit =
    appLogEvents.events foreach onLogEvent

  def onLogEvent(event: AppLogEvent): JQuery = {
    val entry = event.event
    val row: RowContent = toRow(event)
    val stackId = s"stack-${row.linkId}"
    entry.stackTrace foreach { stackTrace =>
      val errorRow = tr(`class` := Hidden, id := stackId)(
        td(colspan := s"$ColumnCount")(pre(stackTrace))
      )
      jQueryTable prepend errorRow.render
    }
    jQueryTable prepend row.content.render
    // Toggles text wrapping for long texts when clicked
    elem(row.cellId) click { (_: JQueryEventObject) =>
      elem(row.cellId) toggleClass CellContent
      false
    }
    elem(row.linkId) click { (_: JQueryEventObject) =>
      elem(stackId) toggleClass Hidden
      false
    }
  }

  // "App", "Time", "Message", "Logger", "Thread", "Level"
  def toRow(event: AppLogEvent): RowContent = {
    val entry = event.event
    val rowClass = entry.level match {
      case "ERROR" => Danger
      case "WARN" => Warning
      case _ => ""
    }
    val entryId = UUID.randomUUID().toString take 5
    val msgCellId = s"msg-$entryId"
    val linkId = s"link-$entryId"
    val level = entry.level
    val levelCell: Modifier = entry.stackTrace
      .map(_ => a(href := "#", id := linkId)(level))
      .getOrElse(level)
    val frag = tr(`class` := rowClass)(
      cell(event.source.name),
      cell(entry.timeFormatted),
      wideCell(entry.message, msgCellId),
      cell(entry.loggerName),
      cell(entry.threadName),
      td(levelCell)
    )
    RowContent(frag, msgCellId, linkId)
  }


  def cell(content: String) =
    toCell(content, CellContent)

  def wideCell(content: String, cellId: String) =
    td(`class` := s"$CellContent $CellWide", id := cellId)(content)

  def toCell(content: String, clazz: String): TypedTag[String] =
    td(`class` := clazz)(content)
}
