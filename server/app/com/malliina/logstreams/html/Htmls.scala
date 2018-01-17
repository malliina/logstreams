package com.malliina.logstreams.html

import com.malliina.logstreams.html.Htmls.{callAttr, js}
import com.malliina.play.models.Username
import com.malliina.play.tags.Bootstrap._
import com.malliina.play.tags.TagPage
import com.malliina.play.tags.Tags._
import controllers.{Logs, UserFeedback, routes}
import play.api.mvc.Call

import scalatags.Text.all._
import scalatags.Text.{GenericAttr, TypedTag}

object Htmls {
  implicit val callAttr = new GenericAttr[Call]

  /**
    * @param appName typically the name of the Scala.js module
    * @param isProd  true if the app runs in production, false otherwise
    * @return HTML templates with either prod or dev javascripts
    */
  def forApp(appName: String, isProd: Boolean): Htmls = {
    val scripts = ScalaScripts.forApp(appName, isProd)
    withLauncher(scripts.optimized)
  }

  def withLauncher(jsFiles: String*) =
    new Htmls(jsFiles.map(file => js(asset(file))): _*)

  def js[V: AttrValue](url: V) = script(src := url)

  def asset(file: String): Call = routes.Logs.versioned(file)
}

class Htmls(scripts: Modifier*) {
  val Status = "status"
  val LogTableId = "log-table"
  val SourceTableId = "source-table"

  val reverse = controllers.routes.Logs

  def logs = baseIndex("logs")(
    headerRow()("Logs ", small(`class` := s"$PullRight $HiddenXs", id := Status)("Initializing...")),
    defaultTable(LogTableId, Seq("App", "Time", "Message", "Logger", "Thread", "Level"))
  )

  def sources = baseIndex("sources")(
    headerRow()("Servers"),
    fullRow(
      defaultTable(SourceTableId, Seq("App", "Address"))
    )
  )

  def users(us: Seq[Username], feedback: Option[UserFeedback]) = baseIndex("users")(
    headerRow()("Users"),
    fullRow(feedback.fold(empty)(feedbackDiv)),
    row(
      div6(
        if (us.isEmpty) {
          leadPara("No users.")
        } else {
          responsiveTable(us)("Username", "Actions") { user =>
            Seq(td(user.name), td(postableForm(reverse.removeUser(user))(button(`class` := s"$BtnDanger $BtnXs")(" Delete"))))
          }
        }
      ),
      div6(
        postableForm(reverse.addUser())(
          inGroup(Logs.UsernameKey, Text, "Username"),
          passwordGroup(Logs.PasswordKey, "Password"),
          blockSubmitButton()("Add User")
        )
      )
    )
  )

  def defaultTable(tableId: String, headers: Seq[String]) =
    table(`class` := TableStripedHoverResponsive, id := tableId)(
      thead(tr(headers.map(h => th(h)))),
      tbody
    )

  def baseIndex(tabName: String)(inner: Modifier*) = {
    def navItem(thisTabName: String, tabId: String, url: Call, glyphiconName: String) = {
      val maybeActive = if (tabId == tabName) Option(`class` := "active") else None
      li(maybeActive)(a(href := url)(glyphIcon(glyphiconName), s" $thisTabName"))
    }

    root("logstreams")(
      divClass(s"$Navbar $NavbarDefault")(
        divClass("wide-content")(
          divClass(NavbarHeader)(
            hamburgerButton,
            a(`class` := NavbarBrand, href := reverse.index())("logstreams")
          ),
          divClass(s"$NavbarCollapse $Collapse")(
            ulClass(s"$Nav $NavbarNav")(
              navItem("Logs", "logs", reverse.index(), "list"),
              navItem("Sources", "sources", reverse.sources(), "home"),
              navItem("Users", "users", reverse.allSources(), "user")
            )
          )
        )
      ),
      divClass("wide-content")(inner)
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
          cssLink(Htmls.asset("css/custom.css")),
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

  def feedbackDiv(feedback: UserFeedback) = {
    val message = feedback.message
    if (feedback.isError) alertDanger(message)
    else alertSuccess(message)
  }

  def postableForm(onAction: Call, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)

  def passwordGroup(elemId: String, labelText: String) =
    inGroup(elemId, Password, labelText)

  def inGroup(elemId: String, inType: String, labelText: String) =
    formGroup(
      labelFor(elemId)(labelText),
      divClass("controls")(
        namedInput(elemId, `type` := inType, `class` := s"$FormControl $InputMd", required)
      )
    )
}
