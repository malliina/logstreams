package com.malliina.logstreams.js

import java.util.UUID

import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLTableElement, HTMLTableRowElement}
import org.scalajs.jquery.JQueryEventObject

import scalatags.Text.TypedTag
import scalatags.Text.all._

case class JVMLogEntry(level: String,
                       message: String,
                       loggerName: String,
                       threadName: String,
                       timeFormatted: String,
                       stackTrace: Option[String] = None)

class ListenerSocket(wsPath: String) extends SocketJS(wsPath) {
  val CellContent = "cell-content"
  val CellWide = "cell-wide"
  val NoWrap = "no-wrap"
  val Hidden = "hidden"

  lazy val jQueryTable = elem("logTable")
  lazy val table = dom.document.getElementById("logTable").asInstanceOf[HTMLTableElement]

  override def handlePayload(payload: String): Unit = {
    val parsed = validate[JVMLogEntry](payload)
    parsed.fold(onInvalidData, onLogEntry)
  }

  def onLogEntry(entry: JVMLogEntry) = {
    val (frag, msgCellId, linkId) = toRow(entry)
    val stackId = s"stack-$linkId"
    entry.stackTrace foreach { stackTrace =>
      val errorRow = tr(`class` := Hidden, id := stackId)(
        td(colspan := "5")(pre(stackTrace))
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

  // "Time", "Message", "Logger", "Thread", "Level"
  def toRow(entry: JVMLogEntry): (Frag, String, String) = {
    val rowClass = entry.level match {
      case "ERROR" => "danger"
      case "WARN" => "warning"
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

  @deprecated
  def rx = ListenerSocket("/rx")
}
