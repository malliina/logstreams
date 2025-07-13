package com.malliina.logstreams.js

import com.malliina.logstreams.models.FrontStrings.*
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object Frontend:
  private val p = Popper
  private val b = Bootstrap
  private val bootstrapCss = BootstrapCss
  private val tempusDominusCss = TempusDominusCss
  private val appCss = AppCss

  def main(args: Array[String]): Unit =
    val log: BaseLogger = BaseLogger.printer
    if has(classes.Socket) then LogsPage(log)
    if has(classes.Sources) then SourcesPage(log)

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
@JSImport("@eonasdan/tempus-dominus/dist/css/tempus-dominus.css", JSImport.Namespace)
object TempusDominusCss extends js.Object

@js.native
@JSImport("./css/logstreams.css", JSImport.Namespace)
object AppCss extends js.Object
