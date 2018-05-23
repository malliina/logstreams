package com.malliina.logstreams.html

import com.malliina.html.{Bootstrap, Tags}
import com.malliina.logstreams.html.Htmls._
import com.malliina.logstreams.models.{AppName, FrontStrings}
import com.malliina.play.models.Username
import com.malliina.play.tags.TagPage
import controllers.Assets.Asset
import controllers.{Logs, UserFeedback, routes}
import play.api.mvc.Call
import play.filters.csrf.CSRF
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
    val suffix = if (isProd) "opt" else "fastopt"
    new Htmls(s"${appName.toLowerCase}-$suffix.js")
  }

  def asset(file: Asset): Call = routes.Logs.versioned(file)
}

class Htmls(mainJs: Asset) extends Bootstrap(Tags) with FrontStrings {

  import tags._

  val Status = "status"

  val reverse = controllers.routes.Logs

  def logs(apps: Seq[AppName]) = baseIndex("logs")(
    headerRow("Logs"),
    row(
      divClass(col.sm.eight)(
        div(id := AppsDropdown, `class` := "dropdown")(
          button(`class` := "btn btn-secondary btn-sm dropdown-toggle", `type` := "button", dataToggle := "dropdown", aria.haspopup := "true", aria.expanded := "false")("Apps"),
          div(`class` := DropdownMenu, id := DropdownMenuId)(
            apps.map(app => a(`class` := DropdownItemId, href := "#")(app.name))
          )
        ),
        div(id := AppsFiltered)
      ),
      div(`class` := s"${col.sm.four} mt-1 mt-sm-0")(
        div(`class` := s"btn-group btn-group-toggle compact-group float-right", role := "group", data("toggle") := "buttons")(
          label(`class` := "btn btn-info btn-sm", id := LabelVerbose)(
            input(`type` := "radio", name := "options", id := "option-verbose", autocomplete := "off")(" Verbose")
          ),
          label(`class` := "btn btn-info btn-sm ", id := LabelCompact)(
            input(`type` := "radio", name := "options", id := "option-compact", autocomplete := "off")(" Compact")
          )
        )
      )
    ),
    logEntriesTable(LogTableId)(thead(id := TableHeadId), tbody(id := TableBodyId))
  )

  def sources = baseIndex("sources")(
    headerRow("Servers"),
    fullRow(
      defaultTable(SourceTableId, Seq("App", "Address"))
    )
  )

  def users(us: Seq[Username], csrf: CSRF.Token, feedback: Option[UserFeedback]) = {
    val csrfInput = input(`type` := "hidden", name := csrf.name, value := raw(csrf.value).render)
    baseIndex("users")(
      headerRow("Users"),
      fullRow(feedback.fold(empty)(feedbackDiv)),
      row(
        div6(
          if (us.isEmpty) {
            leadPara("No users.")
          } else {
            headeredTable(tables.stripedHover, Seq("Username", "Actions"))(
              tbody(us.map { user =>
                tr(
                  td(user.name),
                  td(`class` := "table-button")(
                    postableForm(reverse.removeUser(user))(
                      csrfInput,
                      button(`class` := s"${btn.danger} ${btn.sm}")(" Delete")
                    )
                  )
                )
              })
            )
          }
        ),
        div6(
          postableForm(reverse.addUser())(
            csrfInput,
            inGroup(Logs.UsernameKey, Text, "Username"),
            passwordGroup(Logs.PasswordKey, "Password"),
            blockSubmitButton()("Add User")
          )
        )
      )
    )
  }

  def logEntriesTable(tableId: String) = table(`class` := tables.defaultClass, id := tableId)

  def defaultTable(tableId: String, headers: Seq[String]) =
    defaultTableBase(tableId, headers.map(h => th(h)))

  def defaultTableBase(tableId: String, headers: Modifier) =
    table(`class` := tables.defaultClass, id := tableId)(
      thead(tr(headers)),
      tbody
    )

  def baseIndex(tabName: String)(inner: Modifier*) = {
    def navItem(thisTabName: String, tabId: String, url: Call, iconicName: String) = {
      val itemClass = if (tabId == tabName) "nav-item active" else "nav-item"
      li(`class` := itemClass)(a(href := url, `class` := "nav-link")(iconic(iconicName), s" $thisTabName"))
    }

    root("logstreams")(
      navbar.simple(
        reverse.index(),
        "logstreams",
        modifier(
          navItem("Logs", "logs", reverse.index(), "list"),
          navItem("Sources", "sources", reverse.sources(), "home"),
          navItem("Users", "users", reverse.allUsers(), "person")
        )
      ),
      div(`class` := "wide-content", id := "page-content")(inner)
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
          section(inner),
          jsScript(asset(mainJs))
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
