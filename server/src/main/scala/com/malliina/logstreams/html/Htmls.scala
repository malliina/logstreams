package com.malliina.logstreams.html

import com.malliina.html.HtmlImplicits.fullUrl
import com.malliina.html.{Bootstrap, HtmlTags, TagPage}
import com.malliina.http.FullUrl
import com.malliina.live.LiveReload
import com.malliina.logstreams.html.Htmls.*
import com.malliina.logstreams.http4s.{LogRoutes, UserFeedback}
import com.malliina.logstreams.models.{AppName, FrontStrings, LogLevel}
import com.malliina.values.Username
import org.http4s.Uri
import scalatags.Text.all.*
import scalatags.text.Builder

object Htmls:
  val UsernameKey = "username"
  val PasswordKey = "password"

  implicit val uriAttr: AttrValue[Uri] = (t: Builder, a: Attr, v: Uri) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(v.renderString))

  /** @param appName
    *   typically the name of the Scala.js module
    * @param isProd
    *   true if the app runs in production, false otherwise
    * @return
    *   HTML templates with either prod or dev javascripts
    */
  def forApp(appName: String, isProd: Boolean, assets: AssetsSource): Htmls =
    val name = appName.toLowerCase
    val opt = if isProd then "opt" else "fastopt"
    val externalScripts = if isProd then Nil else FullUrl.build(LiveReload.script).toSeq
    val assetPrefix = s"$name-$opt"
    val appScripts =
      if isProd then Seq(s"$assetPrefix-bundle.js")
      else Seq(s"$assetPrefix-library.js", s"$assetPrefix-loader.js", s"$assetPrefix.js")
    new Htmls(appScripts, externalScripts, Seq(s"$assetPrefix.css", "styles.css"), assets)

class Htmls(
  scripts: Seq[String],
  externalScripts: Seq[FullUrl],
  cssFiles: Seq[String],
  assets: AssetsSource
) extends Bootstrap(HtmlTags)
  with FrontStrings:

  import tags.*

  val Status = "status"
  private val reverse = LogRoutes

  def asset(name: String): Uri = assets.at(name)

  def logs(apps: Seq[AppName]) = baseIndex("logs")(
    headerRow("Logs"),
    row(
      div(`class` := s"col-sm-2 col-md-3 mt-1 mt-sm-0 d-none d-md-block")(
        div(
          `class` := "btn-group",
          role := "group",
          aria.label := "Verbose or compact"
        )(
          input(
            `class` := "btn-check",
            `type` := "radio",
            name := "options",
            id := OptionVerbose,
            autocomplete := "off"
          ),
          label(
            `class` := "btn btn-sm btn-outline-primary",
            `for` := OptionVerbose
          )("Verbose"),
          input(
            `class` := "btn-check",
            `type` := "radio",
            name := "options",
            id := OptionCompact,
            autocomplete := "off"
          ),
          label(
            `class` := "btn btn-sm btn-outline-primary",
            `for` := OptionCompact
          )("Compact")
        )
      ),
      divClass("col-sm-2 col-md-2 col-lg-1")(
        div(id := LogLevelDropdown, `class` := "dropdown")(
          button(
            id := LogLevelDropdownButton,
            `class` := s"btn btn-info btn-sm $DropdownToggle",
            `type` := "button",
            data("bs-toggle") := "dropdown",
            aria.haspopup := "true",
            aria.expanded := "false"
          )("Level"),
          div(`class` := DropdownMenu, id := LogLevelDropdownMenuId)(
            LogLevel.all.map(l => a(`class` := DropdownItem, href := "#")(l.name))
          )
        )
      ),
      divClass("col-sm-6 col-md-7 mt-2 mb-2 mt-sm-0")(
        div(id := AppsDropdown, `class` := "dropdown")(
          button(
            `class` := s"btn btn-primary btn-sm $DropdownToggle",
            `type` := "button",
            data("bs-toggle") := "dropdown",
            aria.haspopup := "true",
            aria.expanded := "false"
          )("Apps"),
          div(`class` := DropdownMenu, id := AppsDropdownMenuId)(
            apps.map(app => a(`class` := DropdownItem, href := "#")(app.name))
          )
        ),
        div(id := AppsFiltered)
      )
    ),
    row(
      div(`class` := "input-group mt-3 mb-3")(
        input(
          `type` := "text",
          `class` := "form-control search-control",
          aria.label := "Search input",
          id := SearchInput,
          placeholder := "Message, stacktrace, thread, ..."
        ),
        button(`type` := "button", `class` := "btn btn-outline-primary", id := SearchButton)(
          "Search"
        )
      )
    ),
    logEntriesTable(LogTableId)(thead(id := TableHeadId), tbody(id := TableBodyId))
  )

  def sources = baseIndex("sources")(
    headerRow("Servers"),
    fullRow(
      defaultTable(SourceTableId, Seq("App", "Address", "ID", "Joined"))
    )
  )

  def users(us: Seq[Username], feedback: Option[UserFeedback]) =
//    val csrfInput = input(`type` := "hidden", name := csrf.name, value := raw(csrf.value).render)
    baseIndex("users")(
      headerRow("Users"),
      fullRow(feedback.fold(empty)(feedbackDiv)),
      row(
        div6(
          if us.isEmpty then leadPara("No users.")
          else
            headeredTable(tables.stripedHover, Seq("Username", "Actions"))(
              tbody(us.map { user =>
                tr(
                  td(user.name),
                  td(`class` := "table-button")(
                    postableForm(reverse.removeUser(user))(
                      button(`class` := s"${btn.danger} ${btn.sm}")(" Delete")
                    )
                  )
                )
              })
            )
        ),
        div6(
          postableForm(reverse.addUser)(
            inGroup(UsernameKey, Text, "Username"),
            passwordGroup(PasswordKey, "Password"),
            blockSubmitButton()("Add User")
          )
        )
      )
    )

  def logEntriesTable(tableId: String) =
    table(`class` := tables.defaultClass, id := tableId)

  def defaultTable(tableId: String, headers: Seq[String]) =
    defaultTableBase(tableId, headers.map(h => th(h)))

  def defaultTableBase(tableId: String, headers: Modifier) =
    table(`class` := tables.defaultClass, id := tableId)(
      thead(tr(headers)),
      tbody
    )

  def baseIndex(tabName: String)(inner: Modifier*) =
    def navItem(thisTabName: String, tabId: String, url: Uri, faName: String) =
      val itemClass = if tabId == tabName then "nav-item active" else "nav-item"
      li(`class` := itemClass)(a(href := url, `class` := "nav-link")(fa(faName), s" $thisTabName"))

    root("logstreams")(
      navbar.simple(
        reverse.index,
        "logstreams",
        modifier(
          navItem("Logs", "logs", reverse.index, "list"),
          navItem("Sources", "sources", reverse.sources, "tower-broadcast"),
          navItem("Users", "users", reverse.allUsers, "user")
        )
      ),
      div(`class` := "wide-content", id := "page-content")(inner)
    )

  def root(titleLabel: String, extraHeader: Modifier*)(inner: Modifier*) =
    TagPage(
      html(lang := "en")(
        head(
          titleTag(titleLabel),
          deviceWidthViewport,
          link(rel := "shortcut icon", `type` := "image/png", href := asset("img/jag-16x16.png")),
          cssFiles.map(file => cssLink(asset(file))),
          extraHeader
        ),
        body(
          section(inner),
          scripts.map { js =>
            jsScript(asset(js), defer)
          },
          externalScripts.map { js =>
            jsScript(js, defer)
          }
        )
      )
    )

  def feedbackDiv(feedback: UserFeedback) =
    val message = feedback.message
    if feedback.isError then alertDanger(message)
    else alertSuccess(message)

  def postableForm(onAction: Uri, more: Modifier*) =
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

  def fa(faName: String) =
    i(`class` := s"nav-icon $faName", title := faName, aria.hidden := tags.True)
