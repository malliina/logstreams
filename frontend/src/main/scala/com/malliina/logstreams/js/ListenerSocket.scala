package com.malliina.logstreams.js

import java.util.UUID

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLTableElement
import org.scalajs.jquery.JQueryEventObject

import scalatags.Text.TypedTag
import scalatags.Text.all._

case class LogSource(name: String, remoteAddress: String)

case class LogEvent(level: String,
                    message: String,
                    loggerName: String,
                    threadName: String,
                    timeFormatted: String,
                    stackTrace: Option[String] = None)

case class AppLogEvent(source: LogSource, event: LogEvent)

case class AppLogEvents(events: Seq[AppLogEvent])

class ListenerSocket(wsPath: String) extends SocketJS(wsPath) {
  val CellContent = "cell-content"
  val CellWide = "cell-wide"
  val ColumnCount = 6
  val Danger = "danger"
  val Hidden = "hidden"
  val NoWrap = "no-wrap"
  val TableId = "logTable"
  val Warning = "warning"

  lazy val jQueryTable = elem(TableId)
  lazy val table = dom.document.getElementById(TableId).asInstanceOf[HTMLTableElement]

  override def handlePayload(payload: String): Unit = {
    val parsed = validate[AppLogEvents](payload)
    parsed.fold(onInvalidData.lift, onLogEvents)
  }

  def onLogEvents(appLogEvents: AppLogEvents) =
    appLogEvents.events foreach onLogEvent

  def onLogEvent(event: AppLogEvent) = {
    val entry = event.event
    val (frag, msgCellId, linkId) = toRow(event)
    val stackId = s"stack-$linkId"
    entry.stackTrace foreach { stackTrace =>
      val errorRow = tr(`class` := Hidden, id := stackId)(
        td(colspan := s"$ColumnCount")(pre(stackTrace))
      )
      jQueryTable prepend errorRow.render
    }
    jQueryTable prepend frag.render
    // Toggles text wrapping for long texts when clicked
    elem(msgCellId) click { (_: JQueryEventObject) =>
      elem(msgCellId) toggleClass CellContent
      false
    }
    elem(linkId) click { (_: JQueryEventObject) =>
      elem(stackId) toggleClass Hidden
      false
    }
  }

  // "App", "Time", "Message", "Logger", "Thread", "Level"
  def toRow(event: AppLogEvent): (Frag, String, String) = {
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
    (frag, msgCellId, linkId)
  }


  def cell(content: String) =
    toCell(content, CellContent)

  def wideCell(content: String, cellId: String) =
    td(`class` := s"$CellContent $CellWide", id := cellId)(content)

  def toCell(content: String, clazz: String): TypedTag[String] =
    td(`class` := clazz)(content)
}

object ListenerSocket {
  def apply(wsPath: String) = new ListenerSocket(s"$wsPath?f=json")

  def web = ListenerSocket("/ws/clients")

  def server = ListenerSocket("/ws/sources")
}
