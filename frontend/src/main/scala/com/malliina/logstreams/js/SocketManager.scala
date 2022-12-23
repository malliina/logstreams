package com.malliina.logstreams.js

import com.malliina.logstreams.models.{AppName, LogLevel}
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.html.Anchor
import org.scalajs.dom.{Event, HTMLButtonElement, HTMLInputElement, MouseEvent}
import scalatags.JsDom.all.*

class SocketManager extends ScriptHelpers:
  private val ActiveClass = "active"
  val settings: Settings = StorageSettings
  private val availableApps =
    elem(AppsDropdownMenuId).getElementsByClassName(DropdownItem).map(_.asInstanceOf[Anchor])
  private var socket: ListenerSocket = socketFor(settings.apps, settings.level, settings.query)

  availableApps.foreach { item =>
    item.onclick =
      (_: MouseEvent) => updateFilter(settings.appendDistinct(AppName(item.textContent)))
  }
  private val availableLogLevels =
    elem(LogLevelDropdownMenuId).getElementsByClassName(DropdownItem).map(_.asInstanceOf[Anchor])
  availableLogLevels.foreach { item =>
    item.onclick = (_: MouseEvent) =>
      LogLevel.build(item.textContent).foreach { level =>
        updateLogLevel(level)
      }
  }
  private val searchInput = getElem[HTMLInputElement](SearchInput)
  settings.query.foreach { q =>
    searchInput.value = q
  }
  getElem[HTMLButtonElement](SearchButton).onclick = (e: MouseEvent) => updateSearch()
  searchInput.onkeydown = (ke: KeyboardEvent) => if ke.key == "Enter" then updateSearch()
  renderActiveLevel(availableLogLevels, settings.level)
  renderApps(settings.apps)

  private def socketFor(apps: Seq[AppName], level: LogLevel, query: Option[String]) =
    ListenerSocket(pathFor(apps, level, query), settings, verboseSupport = true)

  private def renderApps(apps: Seq[AppName]): Unit =
    val buttons = apps.map { app =>
      val selected = button(`type` := "button", `class` := "btn btn-info btn-sm")(app.name).render
      selected.onclick = (_: MouseEvent) => updateFilter(settings.remove(app))
      selected
    }
    val target = elem(AppsFiltered)
    target.innerHTML = ""
    buttons.foreach { btn =>
      target.appendChild(btn)
    }

  private def renderActiveLevel(levels: Seq[Anchor], active: LogLevel): Unit =
    elem(LogLevelDropdownButton).innerHTML = active.name
    levels.foreach { item =>
      if item.textContent == active.name && !item.classList.contains(ActiveClass) then
        item.classList.add(ActiveClass)
      else item.classList.remove(ActiveClass)
    }

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
    val appsQuery = if apps.isEmpty then "" else "&" + apps.map(app => s"app=$app").mkString("&")
    val levelQuery = s"&${LogLevel.Key}=${level.name}"
    val searchQuery = query.fold("")(q => s"&q=$q")
    s"/ws/logs?f=json$appsQuery$levelQuery$searchQuery"

  private def reconnect(apps: Seq[AppName], level: LogLevel, query: Option[String]): Unit =
    socket.close()
    socket.clear()
    socket = socketFor(apps, level, query)
