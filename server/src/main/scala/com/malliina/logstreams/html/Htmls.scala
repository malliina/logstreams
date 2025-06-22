package com.malliina.logstreams.html

import cats.data.NonEmptyList
import com.malliina.html.HtmlImplicits.given
import com.malliina.html.{Bootstrap, HtmlTags}
import com.malliina.http.{CSRFConf, CSRFToken, FullUrl}
import com.malliina.live.LiveReload
import com.malliina.logstreams.db.StreamsQuery
import com.malliina.logstreams.html.Htmls.{*, given}
import com.malliina.logstreams.http4s.{LogRoutes, UserFeedback}
import com.malliina.logstreams.models.{AppName, FrontStrings, LogLevel}
import com.malliina.logstreams.{FileAssets, HashedAssets, Limits}
import com.malliina.values.Username
import org.http4s.Uri
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.text.Builder

object Htmls:
  val UsernameKey = "username"
  val PasswordKey = "password"

  case class PageConf(titleText: String, bodyClasses: Seq[String])

  given AttrValue[Uri] = makeStringAttr(_.renderString)
  given AttrValue[CSRFToken] = makeStringAttr(_.value)

  given Conversion[AppName, Modifier] = (a: AppName) => a.name
  given Conversion[LogLevel, Modifier] = (l: LogLevel) => l.name

  private def makeStringAttr[T](write: T => String): AttrValue[T] =
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(write(v)))

  /** @param appName
    *   typically the name of the Scala.js module
    * @param isProd
    *   true if the app runs in production, false otherwise
    * @return
    *   HTML templates with either prod or dev javascripts
    */
  def forApp(appName: String, isProd: Boolean, assets: AssetsSource, csrfConf: CSRFConf): Htmls =
    val externalScripts = if isProd then Nil else FullUrl.build(LiveReload.script).toSeq

    val appScripts =
      if isProd then Seq(FileAssets.frontend_js)
      else Seq(FileAssets.frontend_js, "frontend-loader.js", "main.js")
    Htmls(
      appScripts,
      externalScripts,
      Seq(FileAssets.frontend_css, FileAssets.styles_css),
      assets,
      csrfConf
    )

class Htmls(
  scripts: Seq[String],
  externalScripts: Seq[FullUrl],
  cssFiles: Seq[String],
  assets: AssetsSource,
  csrfConf: CSRFConf
) extends Bootstrap(HtmlTags)
  with FrontStrings:

  import tags.*

  val Status = "status"
  private val reverse = LogRoutes

  private def asset(name: String): Uri = assets.at(name)
  private def inlineOrUri(name: String) =
    HashedAssets.dataUris.getOrElse(name, asset(name).renderString)

  def logs(apps: Seq[AppName], query: StreamsQuery) =
    baseIndex("logs", bodyClasses = Seq(classes.Socket))(
      headerRow("Logs"),
      row(
        div(cls := s"col-sm-2 col-md-3 mt-1 mt-sm-0 d-none d-md-block")(
          div(
            cls := "btn-group",
            role := "group",
            aria.label := "Verbose or compact"
          )(
            input(
              cls := "btn-check",
              tpe := "radio",
              name := "options",
              id := OptionVerbose,
              autocomplete := "off"
            ),
            label(
              cls := "btn btn-sm btn-outline-primary",
              `for` := OptionVerbose
            )("Verbose"),
            input(
              cls := "btn-check",
              tpe := "radio",
              name := "options",
              id := OptionCompact,
              autocomplete := "off"
            ),
            label(
              cls := "btn btn-sm btn-outline-primary",
              `for` := OptionCompact
            )("Compact")
          )
        ),
        divClass("col-sm-2 col-md-2 col-lg-1")(
          div(id := LogLevelDropdown, cls := "dropdown")(
            button(
              id := LogLevelDropdownButton,
              cls := s"btn btn-info btn-sm $DropdownToggle",
              tpe := "button",
              data("bs-toggle") := "dropdown",
              aria.haspopup := "true",
              aria.expanded := "false"
            )(query.level),
            div(cls := DropdownMenu, id := LogLevelDropdownMenuId)(
              LogLevel.all.map: l =>
                a(
                  cls := names(DropdownItem, if l == query.level then ActiveClass else ""),
                  href := move(query.copy(level = l))
                )(l)
            )
          )
        ),
        divClass("col-sm-6 col-md-7 mt-2 mb-2 mt-sm-0")(
          div(id := AppsDropdown, cls := "dropdown")(
            button(
              cls := s"btn btn-primary btn-sm $DropdownToggle",
              tpe := "button",
              data("bs-toggle") := "dropdown",
              aria.haspopup := "true",
              aria.expanded := "false"
            )("Apps"),
            div(cls := DropdownMenu, id := AppsDropdownMenuId)(
              apps.map: app =>
                val included =
                  query.copy(apps = (query.apps.toSet + Username(app.name)).toList)
                a(cls := DropdownItem, href := move(included))(app)
            )
          ),
          div(id := AppsFiltered)(
            query.apps.map: app =>
              val excluded =
                query.copy(apps = (query.apps.toSet - Username(app.name)).toList)
              a(role := "button", cls := "btn btn-info btn-sm", href := move(excluded))(app)
          )
        )
      ),
      divClass(s"$Row form-row")(
        timePicker("From", FromTimePickerId),
        timePicker("To", ToTimePickerId)
      ),
      divClass(s"$Row form-row")(
        div(cls := "input-group my-3")(
          input(
            tpe := "text",
            cls := "form-control search-control",
            aria.label := "Search input",
            id := SearchInput,
            placeholder := "Message, stacktrace, thread, ..."
          ),
          button(tpe := "button", cls := "btn btn-outline-primary", id := SearchButton)(
            "Search"
          )
        )
      ),
      row(id := LoadingSpinner, cls := "loader mx-auto my-3"),
      row(id := SearchFeedbackRowId, cls := DisplayNone)(
        p(id := SearchFeedbackId)
      ),
      row(cls := classes.MobileList)(
        div(id := MobileContentId)
      ),
      logEntriesTable(LogTableId)(thead(id := TableHeadId), tbody(id := TableBodyId)),
      pageNav(query)
    )

  private def names(ns: String*): String = ns.map(_.trim).filter(_.nonEmpty).mkString(" ")

  private def pageNav(query: StreamsQuery) =
    val prev = query.limits.prev.map(p => move(query.copy(limits = p)))
    val next = move(query.copy(limits = query.limits.next))
    val prevExtra = if prev.isRight then "" else " disabled"
    nav(aria.label := "Navigation", `class` := "d-flex justify-content-center py-3")(
      ul(`class` := "pagination")(
        li(`class` := s"page-item $prevExtra")(
          a(`class` := "page-link", prev.map(p => href := p).getOrElse(href := "#"))("Previous")
        ),
        li(`class` := "page-item")(a(`class` := "page-link", href := next)("Next"))
      )
    )

  private def move(query: StreamsQuery): Uri = toUri(StreamsQuery.toQuery(query))

  private def toUri(qs: Map[String, NonEmptyList[String]]): Uri =
    reverse.logs.withMultiValueQueryParams(qs.map((k, v) => k -> v.toList))

  private def timePicker(labelText: String, divId: String) =
    val inputId = s"$divId-input"
    divClass("col-sm-6 my-2 mt-sm-0")(
      label(`for` := inputId, cls := "form-label")(labelText),
      div(
        id := divId,
        data("td-target-input") := "nearest",
        cls := "input-group"
      )(
        input(
          id := inputId,
          cls := "form-control",
          data("td-target") := s"#$divId"
        ),
        span(
          cls := "input-group-text",
          data("td-target") := s"#$divId",
          data("td-toggle") := "datetimepicker"
        )(
          span(cls := "time-calendar")
        )
      )
    )

  def sources = baseIndex("sources", bodyClasses = Seq(classes.Sources))(
    headerRow("Servers"),
    fullRow(
      defaultTable(SourceTableId, Seq("App", "Address", "User-Agent", "ID", "Joined"))
    )
  )

  def users(us: Seq[Username], feedback: Option[UserFeedback], csrf: CSRFToken) =
    val csrfInput = input(tpe := "hidden", name := csrfConf.tokenName, value := csrf)
    baseIndex("users")(
      headerRow("Users"),
      fullRow(feedback.fold(empty)(feedbackDiv)),
      row(
        div6(
          if us.isEmpty then leadPara("No users.")
          else
            headeredTable(s"${tables.stripedHover} align-middle", Seq("Username", "Actions"))(
              tbody(us.map: user =>
                tr(
                  td(user.name),
                  td(cls := "table-button")(
                    postableForm(reverse.removeUser(user), cls := "table-form")(
                      csrfInput,
                      button(cls := s"${btn.danger} ${btn.sm}")(" Delete")
                    )
                  )
                ))
            )
        ),
        div6(
          form(action := reverse.addUser, method := Post)(
            csrfInput,
            div(cls := "mb-3")(
              label(`for` := UsernameKey, cls := "form-label")("Username"),
              input(tpe := "text", cls := "form-control", id := UsernameKey, name := UsernameKey)
            ),
            div(cls := "mb-3")(
              label(`for` := PasswordKey, cls := "form-label")("Password"),
              input(
                tpe := "password",
                cls := "form-control",
                id := PasswordKey,
                name := PasswordKey
              )
            ),
            button(tpe := "submit", cls := "btn btn-primary")("Add User")
          )
        )
      )
    )

  private def logEntriesTable(tableId: String) =
    table(cls := DisplayNone, id := tableId)

  private def defaultTable(tableId: String, headers: Seq[String]) =
    defaultTableBase(tableId, headers.map(h => th(h)))

  private def defaultTableBase(tableId: String, headers: Modifier) =
    table(cls := tables.defaultClass, id := tableId)(
      thead(tr(headers)),
      tbody
    )

  private def baseIndex(tabName: String, bodyClasses: Seq[String] = Nil)(inner: Modifier*) =
    def navItem(thisTabName: String, tabId: String, url: Uri, faName: String) =
      val itemClass = if tabId == tabName then "nav-item active" else "nav-item"
      li(cls := itemClass)(a(href := url, cls := "nav-link")(fa(faName), s" $thisTabName"))

    root(PageConf("logstreams", bodyClasses))(
      navbar.simple(
        reverse.index,
        "logstreams",
        modifier(
          navItem("Logs", "logs", reverse.index, "list"),
          navItem("Sources", "sources", reverse.sources, "tower-broadcast"),
          navItem("Users", "users", reverse.allUsers, "user")
        )
      ),
      div(cls := "wide-content", id := "page-content")(inner)
    )

  def root(conf: PageConf, extraHeader: Modifier*)(inner: Modifier*): TypedTag[String] =
    html(lang := "en")(
      head(
        titleTag(conf.titleText),
        deviceWidthViewport,
        link(
          rel := "shortcut icon",
          tpe := "image/png",
          href := inlineOrUri("img/jag-16x16.png")
        ),
        cssFiles.map(file => cssLink(asset(file))),
        extraHeader
      ),
      body(cls := conf.bodyClasses.mkString(" "))(
        section(inner),
        scripts.map: js =>
          jsScript(asset(js), defer),
        externalScripts.map: js =>
          jsScript(js, defer)
      )
    )

  private def feedbackDiv(feedback: UserFeedback) =
    val message = feedback.message
    if feedback.isError then alertDanger(message)
    else alertSuccess(message)

  private def postableForm(onAction: Uri, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)

  private def passwordGroup(elemId: String, labelText: String) =
    inGroup(elemId, Password, labelText)

  private def inGroup(elemId: String, inType: String, labelText: String) =
    formGroup(
      labelFor(elemId)(labelText),
      divClass("controls")(
        namedInput(elemId, tpe := inType, cls := s"$FormControl $InputMd", required)
      )
    )

  private def fa(faName: String) =
    i(cls := s"nav-icon $faName", title := faName, aria.hidden := tags.True)
