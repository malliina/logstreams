package com.malliina.logstreams.js

import com.malliina.logstreams.js.ScriptHelpers.{LogTableId, TableBodyId, TableHeadId, VerboseKey, elem, elemOptAs, getElem, OptionVerbose, OptionCompact}
import com.malliina.logstreams.models.{AppLogEvent, AppLogEvents, LogLevel}
import io.circe.Json
import org.scalajs.dom
import org.scalajs.dom.{Event, HTMLElement, HTMLInputElement, HTMLTableElement, document}
import scalatags.JsDom.all.*

case class RowContent(content: Frag, cellId: String, linkId: String)

class ListenerSocket(wsPath: String, settings: Settings, verboseSupport: Boolean)
  extends BaseSocket(wsPath):
  private val CellContent = "cell-content"
  private val CellWide = "cell-wide"
  private val ColumnCount = 6
  private val Danger = "danger"
  private val Hidden = "hidden"
  private val Warning = "warning"
  val Info = "info"

  private val Off = "off"

  val localStorage = dom.window.localStorage

  lazy val tableBody = elem(TableBodyId)
  lazy val table = getElem[HTMLTableElement](LogTableId)

  private def isVerbose: Boolean = settings.isVerbose

  private val responsiveClass = "d-none d-md-table-cell"

  private val responsiveTh = th(`class` := responsiveClass)

  if verboseSupport then
    val verboseClass = names(VerboseKey, if isVerbose then "" else Off)
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
  private val compactInput = getElem[HTMLInputElement](OptionCompact)
  private val verboseInput = getElem[HTMLInputElement](OptionVerbose)
  compactInput.onchange = (e: Event) => if compactInput.checked then updateVerbose(false)
  verboseInput.onchange = (e: Event) => if verboseInput.checked then updateVerbose(true)
  compactInput.checked = !isVerbose
  verboseInput.checked = isVerbose

  private def updateVerbose(newVerbose: Boolean): Unit =
    settings.saveVerbose(newVerbose)
    document.getElementsByClassName(VerboseKey).foreach { e =>
      val element = e.asInstanceOf[HTMLElement]
      val classes = element.classList
      if newVerbose then classes.remove(Off) else classes.add(Off)
    }

  override def handlePayload(payload: Json): Unit =
    handleValidated(payload)(onLogEvents)

  private def onLogEvents(appLogEvents: AppLogEvents): Unit =
    appLogEvents.events.foreach { e => onLogEvent(e) }

  private def onLogEvent(event: AppLogEvent): Unit =
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
      e.onclick = _ => elem(stackId).asInstanceOf[HTMLElement].toggleClass(Hidden)
    }

  // "App", "Time", "Message", "Logger", "Thread", "Level"
  def toRow(event: AppLogEvent): RowContent =
    val entry = event.event
    val rowClass = entry.level match
      case LogLevel.Error => Danger
      case LogLevel.Warn  => Warning
      case _              => Info
    val entryId = randomString(5)
    log.info(s"Rendering $entryId")
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

  private val chars = "abcdefghijklmnopqrstuvwxyz"
  private def randomString(ofLength: Int): String =
    (0 until ofLength).map { _ =>
      chars.charAt(math.floor(math.random() * chars.length).toInt)
    }.mkString

  private def cell(content: String, hideable: Boolean = false, responsive: Boolean = true) =
    toCell(
      content,
      names(
        CellContent,
        if hideable then if isVerbose then VerboseKey else s"$VerboseKey $Off" else "",
        if responsive then responsiveClass else ""
      )
    )

  private def wideCell(content: String, cellId: String) =
    td(`class` := s"$CellContent $CellWide", id := cellId)(content)

  private def toCell(content: String, clazz: String) =
    td(`class` := clazz)(content)

  private def names(ns: String*): String = ns.map(_.trim).filter(_.nonEmpty).mkString(" ")
