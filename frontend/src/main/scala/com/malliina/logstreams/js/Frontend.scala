package com.malliina.logstreams.js

import org.scalajs.dom

object Frontend {
  var app: Option[BaseSocket] = None

  def main(args: Array[String]): Unit = {
    val path = dom.window.location.pathname
    val jsImpl: PartialFunction[String, BaseSocket] = {
      case "/" => ListenerSocket.web
      case "/sources" => new SourcesSocket
    }

    app = jsImpl.lift(path)
  }
}
