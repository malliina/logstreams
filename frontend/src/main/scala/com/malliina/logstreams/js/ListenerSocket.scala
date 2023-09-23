package com.malliina.logstreams.js

import com.malliina.logstreams.js.ScriptHelpers.{elem, elemOptAs, getElem}
import com.malliina.logstreams.models.FrontStrings.*
import com.malliina.logstreams.models.{AppLogEvent, AppLogEvents, FrontEvent, LogEvent, LogLevel, SimpleEvent}
import io.circe.Json
import org.scalajs.dom
import org.scalajs.dom.{Event, HTMLElement, HTMLInputElement, HTMLParagraphElement, HTMLTableElement, document}
import scalatags.JsDom.all.*

case class RowContent(content: Frag, cellId: String, linkId: String, moreId: String)

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

  private lazy val tableBody = elem(TableBodyId)
  private lazy val mobileContent = elem(MobileContentId)
  private lazy val table = getElem[HTMLTableElement](LogTableId)
  private lazy val searchFeedbackRow = elem(SearchFeedbackRowId)
  private lazy val searchFeedback = getElem[HTMLParagraphElement](SearchFeedbackId)
  private lazy val loadingSpinner = getElem[HTMLElement](LoadingSpinner)
  private def isVerbose: Boolean = settings.isVerbose

  private val responsiveClass = "d-none d-md-table-cell"

  private val responsiveTh = th(`class` := responsiveClass)

  if verboseSupport then
    val verboseClass = names(VerboseKey, if isVerbose then "" else Off)
    getElem[HTMLElement](TableHeadId).appendChild(
      tr(
        th("App"),
        th("Time"),
        th("Message"),
        th(`class` := verboseClass)("Logger"),
        th(`class` := verboseClass)("Thread"),
        th(div(`class` := "cell-more-content")),
        th("Level")
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
    handleValidated(payload)(onFrontEvent)

  private def onFrontEvent(event: FrontEvent): Unit =
    event match
      case e @ SimpleEvent(event) =>
        if e == SimpleEvent.loading then
          table.hideFull()
          searchFeedbackRow.hide()
          loadingSpinner.show()
        else if e == SimpleEvent.noData then
          loadingSpinner.hide()
          searchFeedbackRow.show()
          table.hideFull()
          searchFeedback.innerText = "No data for the current query."
        else ()
      case AppLogEvents(events) =>
        loadingSpinner.hide()
        searchFeedbackRow.hide()
        table.className = TableClasses
        events.foreach { e => onLogEvent(e) }

  private def onLogEvent(event: AppLogEvent): Unit =
    mobileContent.prepend(mobileEntry(event).render)
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
    val cell = getElem[HTMLElement](row.cellId)
//    cell.onclick = _ => cell.toggleClass(CellContent)
    // Shows stacktrace if present
    elemOptAs[HTMLElement](row.linkId).foreach { e =>
      e.onclick = _ => elem(stackId).asInstanceOf[HTMLElement].toggleClass(Hidden)
    }
    val moreCell = getElem[HTMLElement](row.moreId)
    moreCell.onclick = _ =>
      moreCell.toggleClass("open")
      cell.toggleClass(CellContent)

  // "App", "Time", "Message", "Logger", "Thread", "More", "Level"
  def toRow(event: AppLogEvent): RowContent =
    val entry = event.event
    val rowClass = entry.level match
      case LogLevel.Error => Danger
      case LogLevel.Warn  => Warning
      case _              => Info
    val entryId = randomString(5)
//    log.info(s"Rendering $entryId")
    val msgCellId = s"msg-$entryId"
    val linkId = s"link-$entryId"
    val moreId = s"more-$entryId"
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
      td(`class` := "cell-more", id := moreId)(a(href := "#")),
      td(`class` := responsiveClass)(levelCell)
    )
    RowContent(frag, msgCellId, linkId, moreId)

  private def mobileEntry(event: AppLogEvent) =
    val entry = event.event
    div(
      div(`class` := "mobile-header")(
        span(`class` := "mobile-app")(event.source.name.name),
        span(entry.timeFormatted)
      ),
      div(`class` := "mobile-message")(event.event.message),
      entry.stackTrace.fold(modifier()) { stackTrace =>
        div(`class` := "mobile-message mobile-error")(stackTrace)
      },
      div(`class` := "mobile-header")(
        div(`class` := "mobile-secondary")(entry.level.name),
        div(`class` := "mobile-secondary")(entry.loggerName)
      ),
      div(`class` := "mobile-secondary")(entry.threadName),
      hr
    )

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
        "" // if responsive then responsiveClass else ""
      )
    )

  private def wideCell(content: String, cellId: String) =
    td(`class` := s"$CellContent $CellWide", id := cellId)(content)

  private def toCell(content: String, clazz: String) =
    td(`class` := clazz)(content)

  private def names(ns: String*): String = ns.map(_.trim).filter(_.nonEmpty).mkString(" ")
