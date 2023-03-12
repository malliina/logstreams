package com.malliina.logstreams.js

import com.malliina.http.FullUrl
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import com.malliina.logstreams.models.FrontStrings.*

object Frontend:
  private val p = Popper
  private val b = Bootstrap
  private val bootstrapCss = BootstrapCss
  private val fontAwesomeCss = FontAwesomeCss

  def main(args: Array[String]): Unit =
    if has(classes.Socket) then SocketManager()
    if has(classes.Sources) then SourcesSocket()
    println("Hi")

  private def has(feature: String) = dom.document.body.classList.contains(feature)

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
