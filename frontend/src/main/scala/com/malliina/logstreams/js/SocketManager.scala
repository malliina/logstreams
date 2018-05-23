package com.malliina.logstreams.js

import com.malliina.logstreams.models.AppName
import org.scalajs.dom.html.Anchor
import org.scalajs.dom.raw.MouseEvent
import scalatags.JsDom.all._

object SocketManager {
  def apply() = new SocketManager
}

class SocketManager extends ScriptHelpers {
  val settings: Settings = StorageSettings
  val availableApps = e(DropdownMenuId).getElementsByClassName(DropdownItemId).map(_.asInstanceOf[Anchor])
  private var socket: ListenerSocket = socketFor(settings.apps)

  availableApps.foreach { item =>
    item.onclick = (_: MouseEvent) => updateFilter(settings.appendDistinct(AppName(item.textContent)))
  }

  renderApps(settings.apps)

  def socketFor(apps: Seq[AppName]) = ListenerSocket(urlFor(apps), settings, verboseSupport = true)

  def renderApps(apps: Seq[AppName]): Unit = {
    val buttons = apps.map { app =>
      val selected = button(`type` := "button", `class` := "btn btn-info btn-sm")(app.name).render
      selected.onclick = (_: MouseEvent) => updateFilter(settings.remove(app))
      selected
    }
    val target = e(AppsFiltered)
    target.innerHTML = ""
    buttons.foreach { btn => target.appendChild(btn) }
  }

  def updateFilter(apps: Seq[AppName]): Unit = {
    renderApps(apps)
    reconnect(apps)
  }

  def urlFor(apps: Seq[AppName]): String = {
    val appsQuery = if (apps.isEmpty) "" else "&" + apps.map(app => s"app=$app").mkString("&")
    s"/ws/logs?f=json$appsQuery"
  }

  def reconnect(apps: Seq[AppName]): Unit = {
    socket.close()
    socket.clear()
    socket = socketFor(apps)
  }
}
