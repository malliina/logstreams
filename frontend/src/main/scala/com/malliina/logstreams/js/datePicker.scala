package com.malliina.logstreams.js

import org.scalajs.dom.{Element, window}

import scala.scalajs.js
import scala.scalajs.js.{Date, UndefOr}
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.JSConverters.JSRichOption

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
  val fi = "fi-FI"
  val se = "sv-SE"
  val en = "en-US"

  def apply(df: DateFormats): TimeLocalization =
    literal(dateFormats = df, hourCycle = "h23", startOfTheWeek = 1, locale = en)
      .asInstanceOf[TimeLocalization]

@js.native
trait TimeRestrictions extends js.Object:
  def minDate: js.UndefOr[Date] = js.native
  def maxDate: js.UndefOr[Date] = js.native

object TimeRestrictions:
  def apply(min: Option[Date], max: Option[Date]): TimeRestrictions =
    literal(minDate = min.orUndefined, maxDate = max.orUndefined).asInstanceOf[TimeRestrictions]

@js.native
trait ButtonOptions extends js.Object:
  def today: Boolean = js.native
  def clear: Boolean = js.native
  def close: Boolean = js.native

object ButtonOptions:
  def apply(today: Boolean = false, clear: Boolean = false, close: Boolean = false): ButtonOptions =
    literal(today = today, clear = clear, close = close).asInstanceOf[ButtonOptions]

@js.native
trait IconOptions extends js.Object:
  def `type`: js.UndefOr[String] = js.native
  def close: js.UndefOr[String] = js.native
  def clear: js.UndefOr[String] = js.native
  def time: js.UndefOr[String] = js.native
  def date: js.UndefOr[String] = js.native
  def up: js.UndefOr[String] = js.native
  def down: js.UndefOr[String] = js.native
  def previous: js.UndefOr[String] = js.native
  def next: js.UndefOr[String] = js.native
  def today: js.UndefOr[String] = js.native

object IconOptions:
  def default = IconOptions(
    "icons",
    "time-picker close",
    "time-picker clear",
    "time-picker time",
    "time-picker date",
    "time-picker up",
    "time-picker down",
    "time-picker previous",
    "time-picker next",
    "time-picker today"
  )
  def apply(
    `type`: String,
    close: String,
    clear: String,
    time: String,
    date: String,
    up: String,
    down: String,
    previous: String,
    next: String,
    today: String
  ): IconOptions =
    literal(
      `type` = `type`,
      close = close,
      clear = clear,
      time = time,
      date = date,
      up = up,
      down = down,
      previous = previous,
      next = next,
      today = today
    ).asInstanceOf[IconOptions]

@js.native
trait DisplayOptions extends js.Object:
  def buttons: ButtonOptions = js.native
  def sideBySide: Boolean = js.native
  def icons: IconOptions = js.native

object DisplayOptions:
  private val smallBreakpointPx = 576

  def basic(close: Boolean) =
    apply(
      ButtonOptions(close = close),
      sideBySide = window.innerWidth >= smallBreakpointPx,
      icons = IconOptions.default
    )

  def apply(
    buttons: ButtonOptions,
    icons: IconOptions,
    sideBySide: Boolean = false
  ): DisplayOptions =
    literal(buttons = buttons, sideBySide = sideBySide, icons = icons).asInstanceOf[DisplayOptions]

@js.native
trait TimeOptions extends js.Object:
  def defaultDate: js.UndefOr[Date] = js.native
  def restrictions: TimeRestrictions = js.native
  def localization: TimeLocalization = js.native
  def display: DisplayOptions = js.native

object TimeOptions:
  def apply(
    defaultDate: Option[Date],
    r: TimeRestrictions,
    l: TimeLocalization,
    display: DisplayOptions,
    useCurrent: Boolean = false,
    promptTimeOnDateChange: Boolean = true
  ) =
    literal(
      defaultDate = defaultDate.orUndefined,
      restrictions = r,
      localization = l,
      display = display,
      useCurrent = useCurrent,
      promptTimeOnDateChange = promptTimeOnDateChange
    ).asInstanceOf[TimeOptions]

@js.native
trait OptionsUpdate extends js.Object:
  def restrictions: TimeRestrictions = js.native

object OptionsUpdate:
  def apply(restrictions: TimeRestrictions): OptionsUpdate =
    literal(restrictions = restrictions).asInstanceOf[OptionsUpdate]

@js.native
trait TimeSubscription extends js.Object:
  def unsubscribe(): Unit = js.native

@js.native
trait BaseEvent extends js.Object:
  def `type`: String = js.native

@js.native
trait ChangeEvent extends DateEvent:
  def isValid: Boolean = js.native
  def isClear: Boolean = js.native

@js.native
trait DateEvent extends BaseEvent:
  def date: js.UndefOr[Date] = js.native

@js.native
@JSImport("@eonasdan/tempus-dominus", "TempusDominus")
class TempusDominus(e: Element, options: TimeOptions) extends js.Object:
  def updateOptions(opts: TimeOptions | OptionsUpdate, reset: Boolean): Unit = js.native
  def viewDate: js.UndefOr[Date] = js.native
  def picked: js.UndefOr[js.Array[Date]] = js.native
  def lastPicked: js.UndefOr[Date] = js.native
  def subscribe(event: String, callback: js.Function1[BaseEvent, Unit]): TimeSubscription =
    js.native
  def clear: Unit = js.native

extension (td: TempusDominus)
  def date: Option[Date] = Option(td).flatMap(p => Option(p.viewDate)).flatMap(_.toOption)

// Seems like UndefOrOps.toOption may return Option(null)
extension [T](undor: js.UndefOr[T]) def opt = undor.toOption.filter(_ != null)
