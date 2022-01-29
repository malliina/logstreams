package com.malliina.logstreams.js

import com.malliina.http.FullUrl
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object Frontend {
  var app: Option[Any] = None

  private val p = Popper
  private val b = Bootstrap
  private val bootstrapCss = BootstrapCss
  private val fontAwesomeCss = FontAwesomeCss

  def main(args: Array[String]): Unit = {
    val location = dom.window.location
    val path = location.pathname
    val jsImpl: PartialFunction[String, Any] = {
      case "/"        => SocketManager()
      case "/sources" => SourcesSocket()
    }

    app = jsImpl.lift(path)
  }
}

@js.native
@JSImport("@popperjs/core", JSImport.Namespace)
object Popper extends js.Object

@js.native
@JSImport("bootstrap", JSImport.Namespace)
object Bootstrap extends js.Object

@js.native
@JSImport("bootstrap/dist/css/bootstrap.min.css", JSImport.Namespace)
object BootstrapCss extends js.Object

@js.native
@JSImport("@fortawesome/fontawesome-free/css/all.min.css", JSImport.Namespace)
object FontAwesomeCss extends js.Object
