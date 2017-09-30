package com.malliina.logstreams.js

import org.scalajs.dom

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

object Frontend extends JSApp {
  var app: Option[BaseSocket] = None

  @JSExport
  override def main(): Unit = {
    val path = dom.window.location.pathname
    val jsImpl: PartialFunction[String, BaseSocket] = {
      case "/" => ListenerSocket.web
      case "/sources" => new SourcesSocket
    }

    app = jsImpl.lift(path)
  }
}
