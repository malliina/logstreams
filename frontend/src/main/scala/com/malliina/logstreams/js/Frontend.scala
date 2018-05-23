package com.malliina.logstreams.js

import org.scalajs.dom

object Frontend {
  var app: Option[Any] = None

  def main(args: Array[String]): Unit = {
    val path = dom.window.location.pathname
    val jsImpl: PartialFunction[String, Any] = {
      case "/" => SocketManager()
      case "/sources" => new SourcesSocket()
    }

    app = jsImpl.lift(path)
  }
}
