package com.malliina.logstreams.js

import java.util.UUID
import com.malliina.logstreams.models.{AppLogEvent, AppLogEvents, LogLevel}
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.raw.{Event, HTMLElement, HTMLTableElement}
import play.api.libs.json.JsValue
import scalatags.JsDom.all._

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
  val ActiveCustom = "active-custom"

  val Off = "off"

  val localStorage = dom.window.localStorage

  lazy val tableBody = elem(TableBodyId)
  lazy val table = getElem[HTMLTableElement](LogTableId)

  def isVerbose: Boolean = settings.isVerbose

  if (verboseSupport) {
    val verboseClass = names(VerboseKey, if (isVerbose) "" else Off)
    getElem[HTMLElement](TableHeadId).appendChild(
      tr(
        th("App"),
        th("Time"),
        th("Message"),
        th(`class` := verboseClass)("Logger"),
        th(`class` := verboseClass)("Thread"),
        th("Level")
      ).render
    )
    configureToggle(LabelVerbose, isVerbose)(_ => updateVerboseByClick(true))
    configureToggle(LabelCompact, !isVerbose)(_ => updateVerboseByClick(false))
  }

  updateVerbose(isVerbose)

  def updateVerbose(newVerbose: Boolean): Unit = {
    settings.saveVerbose(newVerbose)
    document.getElementsByClassName(VerboseKey).foreach { e =>
      val classes = e.asInstanceOf[HTMLElement].classList
      if (newVerbose) classes.remove(Off) else classes.add(Off)
    }
  }

  def updateVerboseByClick(newVerbose: Boolean) = {
    updateVerbose(newVerbose)
    activateCustom(LabelVerbose, false)
    activateCustom(LabelCompact, false)
  }

  def configureToggle(on: String, isActive: Boolean)(onClick: Event => Unit): Unit = {
    val e = activateCustom(on, isActive)
    e.addEventListener("click", onClick)
  }

  private def activateCustom(id: String, isActive: Boolean) = {
    val e = getElem[HTMLElement](id)
    if (isActive) e.classList.add(ActiveCustom)
    else e.classList.remove(ActiveCustom)
    e
  }

  override def handlePayload(payload: JsValue): Unit =
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
      cell(entry.loggerName, hideable = true),
      cell(entry.threadName, hideable = true),
      td(levelCell)
    )
    RowContent(frag, msgCellId, linkId)
  }

  def cell(content: String, hideable: Boolean = false) =
    toCell(
      content,
      names(CellContent, if (hideable) if (isVerbose) VerboseKey else s"$VerboseKey $Off" else "")
    )

  def wideCell(content: String, cellId: String) =
    td(`class` := s"$CellContent $CellWide", id := cellId)(content)

  def toCell(content: String, clazz: String) =
    td(`class` := clazz)(content)

  def names(ns: String*): String = ns.map(_.trim).filter(_.nonEmpty).mkString(" ")
}
