package com.malliina.logstreams.js

import java.util.UUID

import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.raw.{Event, HTMLElement, HTMLTableElement}
import org.scalajs.jquery.{JQuery, JQueryEventObject}
import play.api.libs.json.JsValue
import scalatags.JsDom.all._

case class RowContent(content: Frag, cellId: String, linkId: String)

object ListenerSocket {
  def apply(wsPath: String, verboseSupport: Boolean = false) = new ListenerSocket(s"$wsPath?f=json", verboseSupport)

  def web = ListenerSocket("/ws/logs", verboseSupport = true)
}

class ListenerSocket(wsPath: String, verboseSupport: Boolean) extends BaseSocket(wsPath) {
  val CellContent = "cell-content"
  val CellWide = "cell-wide"
  val ColumnCount = 6
  val Danger = "danger"
  val Hidden = "hidden"
  val NoWrap = "no-wrap"
  val TableId = "log-table"
  val Warning = "warning"

  val VerboseKey = "verbose"
  val localStorage = dom.window.localStorage

  lazy val jQueryTable = elem(TableId)
  lazy val table = document.getElementById(TableId).asInstanceOf[HTMLTableElement]

  var isVerbose: Boolean = Option(localStorage.getItem(VerboseKey)).contains("true")

  if (verboseSupport) {
    val verboseClass = names("verbose", if (isVerbose) "" else "off")
    table.appendChild(thead(tr(
      th("App"),
      th("Time"),
      th("Message"),
      th(`class` := verboseClass)("Logger"),
      th(`class` := verboseClass)("Thread"),
      th("Level"))).render)
    configureToggle("label-verbose", isVerbose)(_ => updateVerbose(true))
    configureToggle("label-compact", !isVerbose)(_ => updateVerbose(false))
  }

  updateVerbose(isVerbose)

  def updateVerbose(newVerbose: Boolean) = {
    isVerbose = newVerbose
    localStorage.setItem(VerboseKey, if (newVerbose) "true" else "false")
    document.getElementsByClassName("verbose").foreach { e =>
      val classes = e.asInstanceOf[HTMLElement].classList
      if (newVerbose) classes.remove("off") else classes.add("off")
    }
  }

  def configureToggle(on: String, isActive: Boolean)(onClick: Event => Unit) = {
    val e = getElem[HTMLElement](on)
    if (isActive) {
      e.classList.add("active")
    }
    e.addEventListener("click", onClick)
  }

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
      cell(entry.loggerName, hideable = true),
      cell(entry.threadName, hideable = true),
      td(levelCell)
    )
    RowContent(frag, msgCellId, linkId)
  }


  def cell(content: String, hideable: Boolean = false) =
    toCell(content, names(CellContent, if (hideable) if (isVerbose) "verbose" else "verbose off" else ""))

  def wideCell(content: String, cellId: String) =
    td(`class` := s"$CellContent $CellWide", id := cellId)(content)

  def toCell(content: String, clazz: String) =
    td(`class` := clazz)(content)

  def names(ns: String*): String = ns.map(_.trim).filter(_.nonEmpty).mkString(" ")
}
