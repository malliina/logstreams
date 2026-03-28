package com.malliina.logstreams.html

import cats.data.NonEmptyList
import com.malliina.html.HtmlImplicits.given
import com.malliina.html.{Bootstrap, HtmlTags}
import com.malliina.http.{CSRFConf, CSRFToken, FullUrl}
import com.malliina.live.LiveReload
import com.malliina.logstreams.db.StreamsQuery
import com.malliina.logstreams.html.Htmls.{*, given}
import com.malliina.logstreams.http4s.{LogRoutes, UserFeedback}
import com.malliina.logstreams.models.{AppName, FrontStrings, Lang, Language, LogLevel, PagingLang}
import com.malliina.logstreams.{FileAssets, HashedAssets}
import com.malliina.values.Username
import org.http4s.Uri
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.text.Builder

object Htmls:
  val UsernameKey = "username"
  val PasswordKey = "password"
  val LanguageKey = "language"

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
    *   HTML templates with either prod or dev JS
    */
  def forApp(appName: String, isProd: Boolean, assets: AssetsSource, csrfConf: CSRFConf): Htmls =
    val externalScripts = if isProd then Nil else FullUrl.build(LiveReload.script).toSeq
    Htmls(
      Seq(FileAssets.main_js),
      externalScripts,
      Seq(FileAssets.main_css),
      assets,
      csrfConf
    )

class Htmls(
  scripts: Seq[String],
  externalScripts: Seq[FullUrl],
  cssFiles: Seq[String],
  assets: AssetsSource,
  csrfConf: CSRFConf
) extends BaseHtml:

  import tags.*

  val Status = "status"
  private val reverse = LogRoutes

  private def asset(name: String): Uri = assets.at(name)
  private def inlineOrUri(name: String) =
    HashedAssets.dataUris.getOrElse(name, asset(name).renderString)

  def profile(language: Language, token: CSRFToken, lang: Lang, feedback: Option[UserFeedback]) =
    baseIndex("profile", lang, bodyClasses = Seq(classes.Profile))(
      Profile(csrfConf)(language, token, lang, feedback)
    )

  def logs(apps: Seq[AppName], query: StreamsQuery, lang: Lang) =
    val llang = lang.logs
    baseIndex("logs", lang, bodyClasses = Seq(classes.Socket))(
      headerRow(llang.title),
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
            )(llang.verbose),
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
            )(llang.compact)
          )
        ),
        divClass("col-sm-2 col-md-2 col-lg-1")(
          div(id := LogLevelDropdown, cls := "dropdown")(
            dropdown(LogLevelDropdownButton, "info")(query.level),
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
            dropdown("dd", "primary")(llang.apps),
            div(cls := DropdownMenu, id := AppsDropdownMenuId)(
              apps.map: app =>
                val included =
                  query.copy(apps = (query.apps.toSet + Username.unsafe(app.name)).toList)
                a(cls := DropdownItem, href := move(included))(app)
            )
          ),
          div(id := AppsFiltered)(
            query.apps.map: app =>
              val excluded =
                query.copy(apps = (query.apps.toSet - Username.unsafe(app.name)).toList)
              a(role := "button", cls := "btn btn-info btn-sm", href := move(excluded))(app)
          )
        )
      ),
      divClass(s"$Row form-row")(
        timePicker(llang.calendar.from, FromTimePickerId),
        timePicker(llang.calendar.to, ToTimePickerId)
      ),
      divClass(s"$Row form-row")(
        div(cls := "input-group my-3")(
          input(
            tpe := "text",
            cls := "form-control search-control",
            aria.label := "Search input",
            id := SearchInput,
            placeholder := llang.searchPlaceholder
          ),
          button(tpe := "button", cls := "btn btn-outline-primary", id := SearchButton)(
            llang.search
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
      pageNav(query, lang.nav.paging)
    )

  private def dropdown(identifier: String, flavor: String) = button(
    id := identifier,
    cls := s"btn btn-$flavor btn-sm $DropdownToggle",
    tpe := "button",
    data("bs-toggle") := "dropdown",
    aria.haspopup := "true",
    aria.expanded := "false"
  )

  private def names(ns: String*): String = ns.map(_.trim).filter(_.nonEmpty).mkString(" ")

  private def pageNav(
    query: StreamsQuery,
    plang: PagingLang,
    divClass: String = "justify-content-center"
  ) =
    val prev = query.limits.prev.map(p => move(query.copy(limits = p)))
    val next = move(query.copy(limits = query.limits.next))
    val prevExtra = if prev.isRight then "" else " disabled"
    nav(aria.label := "Navigation", cls := s"d-flex py-3 $divClass")(
      ul(cls := "pagination")(
        li(cls := s"page-item $prevExtra")(
          a(cls := "page-link", prev.map(p => href := p).getOrElse(href := "#"))(plang.previous)
        ),
        li(cls := "page-item")(a(cls := "page-link", href := next)(plang.next))
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

  def sources(lang: Lang) =
    val slang = lang.servers
    baseIndex("sources", lang, bodyClasses = Seq(classes.Sources))(
      headerRow(lang.servers.title),
      fullRow(
        defaultTable(
          SourceTableId,
          Seq(slang.app, slang.address, slang.userAgent, slang.id, slang.joined)
        )
      )
    )

  def csrfInputField(csrf: CSRFToken) =
    input(tpe := "hidden", name := csrfConf.tokenName, value := csrf)

  def users(us: Seq[Username], feedback: Option[UserFeedback], csrf: CSRFToken, lang: Lang) =
    val ulang = lang.users
    val csrfInput = csrfInputField(csrf)
    baseIndex("users", lang)(
      headerRow(lang.users.title),
      fullRow(feedback.fold(empty)(feedbackDiv)),
      row(
        div6(
          if us.isEmpty then leadPara(ulang.noUsers)
          else
            headeredTable(
              s"${tables.stripedHover} align-middle",
              Seq(ulang.username, ulang.action)
            )(
              tbody(us.map: user =>
                tr(
                  td(user.name),
                  td(cls := "table-button")(
                    postableForm(reverse.removeUser(user), cls := "table-form")(
                      csrfInput,
                      button(cls := s"${btn.danger} ${btn.sm}")(s" ${ulang.deleteUser}")
                    )
                  )
                ))
            )
        ),
        div6(
          form(action := reverse.addUser, method := Post)(
            csrfInput,
            formInput(UsernameKey, ulang.username),
            formInput(PasswordKey, ulang.password, "password"),
            button(tpe := "submit", cls := "btn btn-primary")(ulang.addUser)
          )
        )
      )
    )

  def formInput(
    identifier: String,
    labelText: String,
    inType: String = "text",
    inCls: String = "form-control",
    labelCls: String = "form-label",
    divCls: String = "mb-3"
  ) =
    div(cls := divCls)(
      label(`for` := identifier, cls := labelCls)(labelText),
      input(
        tpe := inType,
        cls := inCls,
        id := identifier,
        name := identifier
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

  private def baseIndex(tabName: String, lang: Lang, bodyClasses: Seq[String] = Nil)(
    inner: Modifier*
  ) =
    def navItem(thisTabName: String, tabId: String, url: Uri, faName: String) =
      val itemClass = if tabId == tabName then "nav-item active" else "nav-item"
      li(cls := itemClass)(a(href := url, cls := "nav-link")(fa(faName), s" $thisTabName"))

    root(PageConf("logstreams", bodyClasses))(
      navbar.simple(
        reverse.index,
        "logstreams",
        modifier(
          navItem(lang.logs.title, "logs", reverse.index, "list"),
          navItem(lang.servers.title, "sources", reverse.sources, "tower-broadcast"),
          navItem(lang.users.title, "users", reverse.allUsers, "user"),
          navItem(lang.profile.title, "profile", reverse.profile, "profile")
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
          href := inlineOrUri(FileAssets.img.jag_16x16_png)
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
