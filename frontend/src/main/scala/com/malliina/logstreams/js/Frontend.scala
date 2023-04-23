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

@js.native
trait DateFormats extends js.Object:
  def LTS: String = js.native
  def LT: String = js.native
  def L: String = js.native
  def LL: String = js.native
  def LLL: String = js.native
  def LLLL: String = js.native

object DateFormats:
  val default = apply(
    "HH:mm:ss",
    "HH:mm",
    "dd-MM-yyyy",
    "MMMM d, yyyy",
    "MMMM d, yyyy HH:mm",
    "dddd, MMMM d, yyyy HH:mm"
  )
  def apply(
    lts: String,
    lt: String,
    l: String,
    ll: String,
    lll: String,
    llll: String
  ): DateFormats =
    literal(LTS = lts, LT = lt, L = l, LL = ll, LLL = lll, LLLL = llll).asInstanceOf[DateFormats]

@js.native
trait TimeLocalization extends js.Object:
  def dateFormats: DateFormats = js.native
  def hourCycle: String = js.native
  def startOfTheWeek: Int = js.native
  def locale: String = js.native

object TimeLocalization:
  def apply(df: DateFormats): TimeLocalization =
    literal(dateFormats = df, hourCycle = "h23", startOfTheWeek = 1, locale = "fi-FI")
      .asInstanceOf[TimeLocalization]

@js.native
trait TimeRestrictions extends js.Object:
  def minDate: js.UndefOr[Date] = js.native
  def maxDate: js.UndefOr[Date] = js.native

object TimeRestrictions:
  def apply(min: Option[Date], max: Option[Date]): TimeRestrictions =
    val obj = (min, max) match
      case (Some(mi), Some(ma)) => literal(minDate = mi, maxDate = ma)
      case (None, Some(ma))     => literal(maxDate = ma)
      case (Some(mi), None)     => literal(minDate = mi)
      case _                    => literal()
    obj.asInstanceOf[TimeRestrictions]

@js.native
trait TimeOptions extends js.Object:
  def restrictions: TimeRestrictions = js.native
  def localization: TimeLocalization = js.native

object TimeOptions:
  def apply(r: TimeRestrictions, l: TimeLocalization) =
    literal(restrictions = r, localization = l).asInstanceOf[TimeOptions]

@js.native
@JSImport("@eonasdan/tempus-dominus", "TempusDominus")
class TempusDominus(e: Element, options: TimeOptions) extends js.Object
