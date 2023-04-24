package com.malliina.logstreams.js

import com.malliina.http.FullUrl
import com.malliina.logstreams.models.FrontStrings.*
import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLElement}

import scala.scalajs.js
import scala.scalajs.js.Date
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.JSConverters.*

object Frontend:
  private val p = Popper
  private val b = Bootstrap
  private val bootstrapCss = BootstrapCss
  private val fontAwesomeCss = FontAwesomeCss
  private val tempusDominusCss = TempusDominusCss

  def main(args: Array[String]): Unit =
    if has(classes.Socket) then LogsPage()
    if has(classes.Sources) then SourcesPage()

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

@js.native
@JSImport("@eonasdan/tempus-dominus/dist/css/tempus-dominus.css", JSImport.Namespace)
object TempusDominusCss extends js.Object
