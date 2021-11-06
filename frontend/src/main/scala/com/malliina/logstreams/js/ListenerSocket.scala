package com.malliina.logstreams.js

import java.util.UUID
import com.malliina.logstreams.models.{AppLogEvent, AppLogEvents, LogLevel}
import io.circe.Json
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.raw.{Event, HTMLElement, HTMLInputElement, HTMLTableElement}
import scalatags.JsDom.all.*

case class RowContent(content: Frag, cellId: String, linkId: String)

object ListenerSocket {
  def apply(wsPath: String, settings: Settings, verboseSupport: Boolean) =
    new ListenerSocket(wsPath, settings, verboseSupport)
}

class ListenerSocket(wsPath: String, settings: Settings, verboseSupport: Boolean)
  extends BaseSocket(wsPath) {
  val CellContent = "cell-content"
  val CellWide = "cell-wide"
  val ColumnCount = 6
  val Danger = "danger"
  val Hidden = "hidden"
  val NoWrap = "no-wrap"
  val Warning = "warning"
  val Info = "info"

  val Off = "off"

  val localStorage = dom.window.localStorage

  lazy val tableBody = elem(TableBodyId)
  lazy val table = getElem[HTMLTableElement](LogTableId)

  def isVerbose: Boolean = settings.isVerbose

  val responsiveClass = "d-none d-md-table-cell"

  val responsiveTh = th(`class` := responsiveClass)

  if (verboseSupport) {
    val verboseClass = names(VerboseKey, if (isVerbose) "" else Off)
    getElem[HTMLElement](TableHeadId).appendChild(
      tr(
        responsiveTh("App"),
        responsiveTh("Time"),
        th("Message"),
        th(`class` := verboseClass)("Logger"),
        th(`class` := verboseClass)("Thread"),
        responsiveTh("Level")
      ).render
    )
  }
  val compactInput = getElem[HTMLInputElement](OptionCompact)
  val verboseInput = getElem[HTMLInputElement](OptionVerbose)
  compactInput.onchange = (e: Event) => {
    if (compactInput.checked) {
      updateVerbose(false)
    }
  }
  verboseInput.onchange = (e: Event) => {
    if (verboseInput.checked) {
      updateVerbose(true)
    }
  }
  compactInput.checked = !isVerbose
  verboseInput.checked = isVerbose

  def updateVerbose(newVerbose: Boolean): Unit = {
    settings.saveVerbose(newVerbose)
    document.getElementsByClassName(VerboseKey).foreach { e =>
      val element = e.asInstanceOf[HTMLElement]
      val classes = element.classList
      if (newVerbose) classes.remove(Off) else classes.add(Off)
    }
  }

  override def handlePayload(payload: Json): Unit =
    handleValidated(payload)(onLogEvents)

  def onLogEvents(appLogEvents: AppLogEvents): Unit =
    appLogEvents.events.foreach { e => onLogEvent(e) }

  def onLogEvent(event: AppLogEvent): Unit = {
    val entry = event.event
    val row: RowContent = toRow(event)
    val stackId = s"stack-${row.linkId}"
    entry.stackTrace.foreach { stackTrace =>
      val errorRow = tr(`class` := Hidden, id := stackId)(
        td(colspan := s"$ColumnCount")(pre(stackTrace))
      )
      tableBody.insertBefore(errorRow.render, tableBody.firstChild)
    }
    tableBody.insertBefore(row.content.render, tableBody.firstChild)
    // Toggles text wrapping for long texts when clicked
    getElem[HTMLElement](row.cellId).onClickToggleClass(CellContent)
    // Shows stacktrace if present
    elemOptAs[HTMLElement](row.linkId).foreach { e =>
      e.onclick = _ => {
        elem(stackId).asInstanceOf[HTMLElement].toggleClass(Hidden)
      }
    }
  }

  // "App", "Time", "Message", "Logger", "Thread", "Level"
  def toRow(event: AppLogEvent): RowContent = {
    val entry = event.event
    val rowClass = entry.level match {
      case LogLevel.Error => Danger
      case LogLevel.Warn  => Warning
      case _              => Info
    }
    val entryId = UUID.randomUUID().toString take 5
    val msgCellId = s"msg-$entryId"
    val linkId = s"link-$entryId"
    val level = entry.level
    val levelCell: Modifier = entry.stackTrace
      .map(_ => a(href := "#", id := linkId)(level.name))
      .getOrElse(level.name)
    val frag = tr(`class` := rowClass)(
      cell(event.source.name.name),
      cell(entry.timeFormatted),
      wideCell(entry.message, msgCellId),
      cell(entry.loggerName, hideable = true, responsive = false),
      cell(entry.threadName, hideable = true, responsive = false),
      td(`class` := responsiveClass)(levelCell)
    )
    RowContent(frag, msgCellId, linkId)
  }

  def cell(content: String, hideable: Boolean = false, responsive: Boolean = true) =
    toCell(
      content,
      names(
        CellContent,
        if (hideable) if (isVerbose) VerboseKey else s"$VerboseKey $Off" else "",
        if (responsive) responsiveClass else ""
      )
    )

  def wideCell(content: String, cellId: String) =
    td(`class` := s"$CellContent $CellWide", id := cellId)(content)

  def toCell(content: String, clazz: String) =
    td(`class` := clazz)(content)

  def names(ns: String*): String = ns.map(_.trim).filter(_.nonEmpty).mkString(" ")
}
