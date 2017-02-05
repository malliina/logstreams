package com.malliina.logstreams.tags

import com.malliina.logstreams.tags.Htmls.callAttr
import com.malliina.logstreams.tags.Htmls.js
import com.malliina.play.tags.Bootstrap._
import com.malliina.play.tags.TagPage
import com.malliina.play.tags.Tags._
import controllers.routes.Assets.at
import controllers.routes
import play.api.mvc.Call

import scalatags.Text.GenericAttr
import scalatags.Text.all._

object Htmls {
  implicit val callAttr = new GenericAttr[Call]

  /**
    * @param appName typically the name of the Scala.js module
    * @param isProd  true if the app runs in production, false otherwise
    * @return HTML templates with either prod or dev javascripts
    */
  def forApp(appName: String, isProd: Boolean): Htmls = {
    val scripts = ScalaScripts.forApp(appName, isProd)
    withLauncher(scripts.optimized, scripts.launcher)
  }

  def withLauncher(jsFiles: String*) =
    new Htmls(jsFiles.map(file => js(at(file))): _*)

  def js[V: AttrValue](url: V) = script(src := url)
}

class Htmls(scripts: Modifier*) {
  val Status = "status"
  val TableId = "logTable"
  val TableBodyId = "logTableBody"

  def logs = baseIndex("logs")(
    headerRow()("Logs ", small(`class` := s"$PullRight $HiddenXs", id := Status)("Initializing...")),
    logTable(Seq("App", "Time", "Message", "Logger", "Thread", "Level"))
  )

  def sources = baseIndex("sources")(
    headerRow()("Servers"),
    fullRow(
      logTable(Seq("Message"))
    )
  )

  def logTable(headers: Seq[String]) =
    table(`class` := TableStripedHoverResponsive, id := TableId)(
      thead(tr(headers.map(h => th(h)))),
      tbody(id := TableBodyId)
    )

  def baseIndex(tabName: String)(inner: Modifier*) = {
    def navItem(thisTabName: String, tabId: String, url: Call, glyphiconName: String) = {
      val maybeActive = if (tabId == tabName) Option(`class` := "active") else None
      li(maybeActive)(a(href := url)(glyphIcon(glyphiconName), s" $thisTabName"))
    }

    root("logstreams")(
      divClass(s"$Navbar $NavbarDefault")(
        divContainer(
          divClass(NavbarHeader)(
            hamburgerButton,
            a(`class` := NavbarBrand, href := routes.Logs.index())("LogStreams")
          ),
          divClass(s"$NavbarCollapse $Collapse")(
            ulClass(s"$Nav $NavbarNav")(
              navItem("Logs", "logs", routes.Logs.index(), "list"),
              navItem("Sources", "sources", routes.Logs.sources(), "home")
            )
          )
        )
      ),
      divClass(Container)(inner)
    )
  }

  def root(titleLabel: String, extraHeader: Modifier*)(inner: Modifier*) =
    TagPage(
      html(
        head(
          titleTag(titleLabel),
          deviceWidthViewport,
          cssLink("//netdna.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css"),
          cssLink("//netdna.bootstrapcdn.com/font-awesome/3.2.1/css/font-awesome.css"),
          cssLink("//ajax.googleapis.com/ajax/libs/jqueryui/1.10.4/themes/smoothness/jquery-ui.css"),
          cssLink(at("css/custom.css")),
          extraHeader,
          js("//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"),
          js("//ajax.googleapis.com/ajax/libs/jqueryui/1.10.4/jquery-ui.min.js"),
          js("//netdna.bootstrapcdn.com/bootstrap/3.1.1/js/bootstrap.min.js")
        ),
        body(
          section(
            inner,
            scripts
          )
        )
      )
    )
}
