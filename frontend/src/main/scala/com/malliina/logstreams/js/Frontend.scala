package com.malliina.logstreams.js

import org.scalajs.dom

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

object Frontend extends JSApp {
  var app: Option[SocketJS] = None

  @JSExport
  override def main() = {
    val path = dom.window.location.pathname
    val jsImpl: PartialFunction[String, SocketJS] = {
      case "/" => ListenerSocket.web
    }

    app = jsImpl.lift(path)
  }
}
