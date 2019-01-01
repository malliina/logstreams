package com.malliina.logstreams.js

import org.scalajs.dom
import org.scalajs.jquery.JQueryStatic

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object Frontend {
  var app: Option[Any] = None

  private val jq = MyJQuery
  private val p = Popper
  private val b = Bootstrap

  def main(args: Array[String]): Unit = {
    val path = dom.window.location.pathname
    val jsImpl: PartialFunction[String, Any] = {
      case "/" => SocketManager()
      case "/sources" => new SourcesSocket()
    }

    app = jsImpl.lift(path)
  }
}

@js.native
@JSImport("jquery", JSImport.Namespace)
object MyJQuery extends JQueryStatic

@js.native
@JSImport("popper.js", JSImport.Namespace)
object Popper extends js.Object

@js.native
@JSImport("bootstrap", JSImport.Namespace)
object Bootstrap extends js.Object
