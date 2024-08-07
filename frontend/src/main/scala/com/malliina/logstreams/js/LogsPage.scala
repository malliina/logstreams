package com.malliina.logstreams.js

import com.malliina.logstreams.js.ScriptHelpers.{AppsDropdownMenuId, AppsFiltered, DropdownItem, FromTimePickerId, LogLevelDropdownButton, LogLevelDropdownMenuId, SearchButton, SearchInput, elem, getElem}
import com.malliina.logstreams.models.FrontStrings.ToTimePickerId
import com.malliina.logstreams.models.{AppName, LogLevel, Queries}
import org.scalajs.dom.html.Anchor
import org.scalajs.dom.{HTMLButtonElement, HTMLInputElement, KeyboardEvent, MouseEvent}
import scalatags.JsDom.all.*

import scala.scalajs.js
import scala.scalajs.js.{Date, URIUtils}

class LogsPage(log: BaseLogger):
  private val qs = QueryString.parse
  private val ActiveClass = "active"
  val settings: Settings = StorageSettings
  private val availableApps =
    elem(AppsDropdownMenuId).getElementsByClassName(DropdownItem).map(_.asInstanceOf[Anchor])
  private var selectedFrom: Option[Date] = Option(defaultFromDate())
  private var selectedTo: Option[Date] = None
  def maxDate = new Date(Date.now())
  private val fromPicker = makePicker(FromTimePickerId, selectedFrom)
  private val toPicker = makePicker(ToTimePickerId, None)
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
  private var socket: ListenerSocket = socketFor(settings.apps, settings.level, settings.query)
  availableApps.foreach: item =>
    item.onclick =
      (_: MouseEvent) => updateFilter(settings.appendDistinct(AppName(item.textContent)))
  private val availableLogLevels =
    elem(LogLevelDropdownMenuId).getElementsByClassName(DropdownItem).map(_.asInstanceOf[Anchor])
  availableLogLevels.foreach: item =>
    item.onclick = (_: MouseEvent) =>
      LogLevel
        .build(item.textContent)
        .foreach: level =>
          updateLogLevel(level)
  private val searchInput = getElem[HTMLInputElement](SearchInput)
  settings.query.foreach: q =>
    searchInput.value = q
  getElem[HTMLButtonElement](SearchButton).onclick = (e: MouseEvent) => updateSearch()
  searchInput.onkeydown = (ke: KeyboardEvent) => if ke.key == "Enter" then updateSearch()
  renderActiveLevel(availableLogLevels, settings.level)
  renderApps(settings.apps)
  val fromSub = subscribeDate(fromPicker, toPicker, isFrom = true)
  val toSub = subscribeDate(toPicker, fromPicker, isFrom = false)
  private def subscribeDate(picker: TempusDominus, other: TempusDominus, isFrom: Boolean) =
    picker.subscribe(
      "hide.td",
      e =>
        val ce = e.asInstanceOf[DateEvent]
        val newDate = ce.date.opt
        if isFrom then selectedFrom = newDate else selectedTo = newDate
        newDate.foreach: date =>
          other.updateOptions(
            OptionsUpdate(
              if isFrom then TimeRestrictions(min = newDate, max = Option(maxDate))
              else TimeRestrictions(min = None, max = newDate)
            ),
            reset = false
          )
        updateSearch()
    )

  private def socketFor(apps: Seq[AppName], level: LogLevel, query: Option[String]) =
    qs.set(LogLevel.Key, level.name)
    qs.delete(AppName.Key)
    apps.foreach: app =>
      qs.append(AppName.Key, app.name)
    qs.setOrDelete(Queries.Q, query)
    qs.setOrDelete(Queries.From, selectedFrom.map(_.toISOString()))
    qs.setOrDelete(Queries.To, selectedTo.map(_.toISOString()))
    qs.commit()
    // This opens a websocket
    ListenerSocket(pathFor(apps, level, query), settings, verboseSupport = true)

  private def renderApps(apps: Seq[AppName]): Unit =
    val buttons = apps.map: app =>
      val selected = button(`type` := "button", `class` := "btn btn-info btn-sm")(app.name).render
      selected.onclick = (_: MouseEvent) => updateFilter(settings.remove(app))
      selected
    val target = elem(AppsFiltered)
    target.innerHTML = ""
    buttons.foreach: btn =>
      target.appendChild(btn)

  private def renderActiveLevel(levels: Seq[Anchor], active: LogLevel): Unit =
    elem(LogLevelDropdownButton).innerHTML = active.name
    levels.foreach: item =>
      if item.textContent == active.name && !item.classList.contains(ActiveClass) then
        item.classList.add(ActiveClass)
      else item.classList.remove(ActiveClass)

  private def updateFilter(apps: Seq[AppName]): Unit =
    renderApps(apps)
    reconnect(apps, settings.level, settings.query)

  private def updateLogLevel(level: LogLevel): Unit =
    settings.saveLevel(level)
    renderActiveLevel(availableLogLevels, level)
    reconnect(settings.apps, level, settings.query)

  private def updateSearch(): Unit =
    val text = searchInput.value
    val query = Option(text).filter(_.length >= 3)
    settings.saveQuery(query)
    reconnect(settings.apps, settings.level, query)

  private def pathFor(apps: Seq[AppName], level: LogLevel, query: Option[String]): String =
    // TODO read from query string
    val appsParams = apps.map(app => AppName.Key -> app.name)
    val levelParam = Seq(LogLevel.Key -> level.name)
    val searchQuery = query.map(q => Queries.Q -> q).toList
    val f = selectedFrom.map(d => Queries.From -> d.toISOString())
    val t = selectedTo.map(d => Queries.To -> d.toISOString())
    val dates = f.toList ++ t.toList
    val params = (Seq("f" -> "json") ++ appsParams ++ levelParam ++ searchQuery ++ dates)
      .map((k, v) => s"$k=${URIUtils.encodeURIComponent(v)}")
      .mkString("&")
    s"/ws/logs?$params"

  private def defaultFromDate(now: Date = new Date(Date.now())) =
    val from = new Date(Date.now())
    // now minus two days
    from.setDate(from.getDate() - 2)
    from

  private def reconnect(apps: Seq[AppName], level: LogLevel, query: Option[String]): Unit =
    socket.close()
    socket.clear()
    socket = socketFor(apps, level, query)
