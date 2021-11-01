package com.malliina.logstreams.models

import com.malliina.html.{Tags, BootstrapStrings, BootstrapParts}

/** Scalatags for Twitter Bootstrap.
  */
class Bootstrap5[Builder, Output <: FragT, FragT](val tags: Tags[Builder, Output, FragT])
  extends BootstrapStrings
  with BootstrapParts {

  import tags._
  import tags.impl.all._

  val dataParent = data("parent")
  val dataTarget = data("bs-target")
  val dataToggle = data("bs-toggle")

  val nav = tag(Nav)

  object navbar {

    import navbars._

    def simple[V: AttrValue](
      home: V,
      appName: Modifier,
      navItems: Modifier,
      navClass: String = DefaultLight,
      navBarId: String = defaultNavbarId
    ) =
      basic(home, appName, ulClass(s"${navbars.Nav} $MrAuto")(navItems), navClass, navBarId)

    def basic[V: AttrValue](
      home: V,
      appName: Modifier,
      navContent: Modifier,
      navClass: String = DefaultLight,
      navBarId: String = defaultNavbarId
    ) =
      nav(`class` := navClass)(
        divClass(Container)(
          a(`class` := Brand, href := home)(appName),
          button(
            `class` := Toggler,
            dataToggle := CollapseWord,
            dataTarget := s"#$navBarId",
            aria.controls := navBarId,
            aria.expanded := False,
            aria.label := "Toggle navigation"
          )(
            spanClass(TogglerIcon)
          ),
          div(`class` := s"$CollapseWord ${navbars.Collapse}", id := navBarId)(
            navContent
          )
        )
      )
  }

  def alertDanger(message: String) = alertDiv(alert.danger, message)

  def alertSuccess(message: String) = alertDiv(alert.success, message)

  def alertDiv(alertClass: String, message: String) =
    divClass(s"$Lead $alertClass", role := alert.Alert)(message)

  def leadPara = pClass(Lead)

  def headerRow(header: Modifier, clazz: String = col.md.twelve) =
    rowColumn(clazz)(
      headerDiv(
        h1(header)
      )
    )

  def fullRow(inner: Modifier*) = rowColumn(col.md.twelve)(inner)

  def halfRow(inner: Modifier*) = rowColumn(col.md.six)(inner)

  def rowColumn(clazz: String)(inner: Modifier*) = row(divClass(clazz)(inner))

  def row = divClass(Row)

  def div4 = divClass(col.md.four)

  def div6 = divClass(col.md.six)

  def divContainer = divClass(Container)

  def formGroup = divClass(FormGroup)

  def headerDiv = divClass(PageHeader)

  def defaultSubmitButton = submitButton(`class` := btn.default)

  def blockSubmitButton(more: Modifier*) =
    submitButton(`class` := s"${btn.primary} ${btn.block}", more)

  def responsiveTable[T](entries: Seq[T])(headers: String*)(cells: T => Seq[Modifier]) =
    headeredTable(tables.stripedHoverResponsive, headers.map(stringFrag))(
      tbody(entries.map(entry => tr(cells(entry))))
    )
}
