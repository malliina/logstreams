package com.malliina.logstreams.html

import com.malliina.logstreams.html.Htmls._
import com.malliina.play.models.Username
import com.malliina.play.tags.Bootstrap._
import com.malliina.play.tags.TagPage
import com.malliina.play.tags.Tags._
import controllers.Assets.Asset
import controllers.{Logs, UserFeedback, routes}
import play.api.mvc.Call

import scalatags.Text.GenericAttr
import scalatags.Text.all._

object Htmls {
  implicit val callAttr = new GenericAttr[Call]

  val crossorigin = attr("crossorigin")
  val integrity = attr("integrity")

  val Anonymous = "anonymous"

  /**
    * @param appName typically the name of the Scala.js module
    * @param isProd  true if the app runs in production, false otherwise
    * @return HTML templates with either prod or dev javascripts
    */
  def forApp(appName: String, isProd: Boolean): Htmls = {
    val suffix = if (isProd) "opt" else "fastopt"
    new Htmls(s"${appName.toLowerCase}-$suffix.js")
  }

  def jsAsset(file: Asset) = js(asset(file))

  def asset(file: Asset): Call = routes.Logs.versioned(file)

  def jsHashed[V: AttrValue](url: V, integrityHash: String, more: Modifier*) =
    script(src := url, integrity := integrityHash, crossorigin := Anonymous, more)

  def js[V: AttrValue](url: V, more: Modifier*) = script(src := url, more)

  def cssLinkHashed[V: AttrValue](url: V, integrityHash: String, more: Modifier*) =
    cssLink2(url, integrity := integrityHash, crossorigin := Anonymous, more)

  def cssLink2[V: AttrValue](url: V, more: Modifier*) =
    link(rel := "stylesheet", href := url, more)

  def iconic(iconicName: String) = spanClass(s"oi oi-$iconicName", title := iconicName, aria.hidden := "true")
}

class Htmls(mainJs: Asset) {
  val Status = "status"
  val LogTableId = "log-table"
  val SourceTableId = "source-table"

  val reverse = controllers.routes.Logs

  def logs = baseIndex("logs")(
    headerRow()("Logs "),
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
          headeredTable("table table-striped table-hover table-sm", Seq("Username", "Actions"))(
            tbody(us.map(user => tr(td(user.name), td(postableForm(reverse.removeUser(user))(button(`class` := s"$BtnDanger $BtnSm")(" Delete"))))))
          )
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
//    table(`class` := TableStripedHoverResponsive, id := tableId)(
    table(`class` := "table table-striped table-hover table-sm", id := tableId)(
      thead(tr(headers.map(h => th(h)))),
      tbody
    )


  def baseIndex(tabName: String)(inner: Modifier*) = {
    def navItem(thisTabName: String, tabId: String, url: Call, iconicName: String) = {
      val itemClass = if (tabId == tabName) "nav-item active" else "nav-item"
      li(`class` := itemClass)(a(href := url, `class` := "nav-link")(iconic(iconicName), s" $thisTabName"))
    }

    val navBarId = "navbarSupportedContent"

    root("logstreams")(
      nav(`class` := s"$Navbar navbar-expand-lg navbar-light bg-light")(
        divClass(Container)(
          divClass(NavbarHeader)(
            a(`class` := NavbarBrand, href := reverse.index())("logstreams"),
            button(`class` := "navbar-toggler", dataToggle := Collapse, dataTarget := s".$NavbarCollapse",
              aria.controls := navBarId, aria.expanded := "false", aria.label := "Toggle navigation")(
              spanClass("navbar-toggler-icon")
            )
          ),
          div(`class` := s"$Collapse $NavbarCollapse", id := navBarId)(
            ulClass(s"$NavbarNav mr-auto")(
              navItem("Logs", "logs", reverse.index(), "list"),
              navItem("Sources", "sources", reverse.sources(), "home"),
              navItem("Users", "users", reverse.allUsers(), "person")
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
          cssLinkHashed("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css", "sha384-Gn5384xqQ1aoWXA+058RXPxPg6fy4IWvTNh0E263XmFcJlSAwiGgFAW/dAiS6JXm"),
          cssLink("https://use.fontawesome.com/releases/v5.0.6/css/all.css"),
          cssLink(Htmls.asset("css/main.css")),
          jsHashed("https://code.jquery.com/jquery-3.2.1.slim.min.js", "sha384-KJ3o2DKtIkvYIK3UENzmM7KCkRr/rE9/Qpg6aAZGJwFDMVNA/GpGFF93hXpG5KkN"),
          jsHashed("https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.12.9/umd/popper.min.js", "sha384-ApNbgh9B+Y1QKtv3Rn7W3mgPxhU9K/ScQsAP7hUibX39j7fakFPskvXusvfa0b4Q"),
          jsHashed("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/js/bootstrap.min.js", "sha384-JZR6Spejh4U02d8jOt6vLEHfe/JQGiRRSQQxSfFWpi1MquVdAyjUar5+76PVCmYl"),
          extraHeader,
        ),
        body(
          section(
            inner,
            Htmls.jsAsset(mainJs)
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
