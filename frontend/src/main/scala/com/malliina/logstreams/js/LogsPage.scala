package com.malliina.logstreams.js

import com.malliina.logstreams.js.ScriptHelpers.*
import com.malliina.logstreams.models.FrontStrings.ToTimePickerId
import com.malliina.logstreams.models.Queries
import org.scalajs.dom.{HTMLButtonElement, HTMLInputElement, KeyboardEvent, MouseEvent, window}

import scala.scalajs.js
import scala.scalajs.js.Date

class LogsPage(log: BaseLogger):
  val settings: Settings = StorageSettings
  private def dateInQuery(key: String) = QueryString.parse.get(key).map(str => new Date(str))
  def maxDate = new Date(Date.now())
  private val fromPicker = makePicker(FromTimePickerId, dateInQuery(Queries.From))
  private val toPicker = makePicker(ToTimePickerId, dateInQuery(Queries.To))
  private def makePicker(elementId: String, initialDate: Option[Date]): TempusDominus =
    TempusDominus(
      elem(elementId),
      TimeOptions(
        initialDate,
        TimeRestrictions(None, None),
        TimeLocalization(DateFormats.default),
        DisplayOptions.basic(close = true)
      )
    )
  private val socket: ListenerSocket = ListenerSocket(pathFor(), settings, verboseSupport = true)
  private val searchInput = getElem[HTMLInputElement](SearchInput)
  getElem[HTMLButtonElement](SearchButton).onclick = (e: MouseEvent) => updateSearch()
  searchInput.onkeydown = (ke: KeyboardEvent) => if ke.key == "Enter" then updateSearch()
  val fromSub = subscribeDate(fromPicker, toPicker, isFrom = true)
  val toSub = subscribeDate(toPicker, fromPicker, isFrom = false)

  private def subscribeDate(picker: TempusDominus, other: TempusDominus, isFrom: Boolean) =
    picker.subscribe(
      "hide.td",
      e =>
        val ce = e.asInstanceOf[DateEvent]
        val newDate = ce.date.opt
        newDate.foreach: date =>
          other.updateOptions(
            OptionsUpdate(
              if isFrom then TimeRestrictions(min = newDate, max = Option(maxDate))
              else TimeRestrictions(min = None, max = newDate)
            ),
            reset = false
          )
        val q = if isFrom then Queries.From else Queries.To
        navigate: qs =>
          qs.setOrDelete(q, newDate.map(_.toISOString()))
    )

  private def updateSearch(): Unit =
    val query = Option(searchInput.value).filter(_.length >= 3)
    navigate: qs =>
      qs.setOrDelete(Queries.Q, query)

  private def navigate(code: QueryString => Unit): Unit =
    val updated = QueryString.modify: qs =>
      code(qs)
    window.location.assign(updated.withPath)

  private def pathFor(): String =
    val qs = QueryString.parse.render
    s"/ws/logs?$qs"
