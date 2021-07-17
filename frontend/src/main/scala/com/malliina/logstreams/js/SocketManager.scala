package com.malliina.logstreams.js

import com.malliina.logstreams.models.{AppName, LogLevel}
import org.scalajs.dom.html.Anchor
import org.scalajs.dom.raw.MouseEvent
import scalatags.JsDom.all._

object SocketManager {
  def apply() = new SocketManager
}

class SocketManager extends ScriptHelpers {
  val ActiveClass = "active"
  val settings: Settings = StorageSettings
  private val availableApps =
    elem(AppsDropdownMenuId).getElementsByClassName(DropdownItem).map(_.asInstanceOf[Anchor])
  private var socket: ListenerSocket = socketFor(settings.apps, settings.level)

  availableApps.foreach { item =>
    item.onclick =
      (_: MouseEvent) => updateFilter(settings.appendDistinct(AppName(item.textContent)))
  }
  val availableLogLevels =
    elem(LogLevelDropdownMenuId).getElementsByClassName(DropdownItem).map(_.asInstanceOf[Anchor])
  availableLogLevels.foreach { item =>
    item.onclick = (_: MouseEvent) =>
      LogLevel.build(item.textContent).foreach { level =>
        updateLogLevel(level)
      }
  }
  renderActiveLevel(availableLogLevels, settings.level)
  renderApps(settings.apps)

  def socketFor(apps: Seq[AppName], level: LogLevel) =
    ListenerSocket(urlFor(apps, level), settings, verboseSupport = true)

  def renderApps(apps: Seq[AppName]): Unit = {
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
  }

  private def renderActiveLevel(levels: Seq[Anchor], active: LogLevel): Unit =
    levels.foreach { item =>
      if (item.textContent == active.name && !item.classList.contains(ActiveClass))
        item.classList.add(ActiveClass)
      else
        item.classList.remove(ActiveClass)
    }

  private def updateFilter(apps: Seq[AppName]): Unit = {
    renderApps(apps)
    reconnect(apps, settings.level)
  }

  private def updateLogLevel(level: LogLevel): Unit = {
    settings.saveLevel(level)
    renderActiveLevel(availableLogLevels, level)
    reconnect(settings.apps, level)
  }

  private def urlFor(apps: Seq[AppName], level: LogLevel): String = {
    val appsQuery = if (apps.isEmpty) "" else "&" + apps.map(app => s"app=$app").mkString("&")
    val levelQuery = s"&${LogLevel.Key}=${level.name}"
    s"/ws/logs?f=json$appsQuery$levelQuery"
  }

  private def reconnect(apps: Seq[AppName], level: LogLevel): Unit = {
    socket.close()
    socket.clear()
    socket = socketFor(apps, level)
  }
}
