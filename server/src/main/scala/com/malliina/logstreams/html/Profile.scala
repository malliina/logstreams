package com.malliina.logstreams.html

import com.malliina.logstreams.models.Language
import scalatags.Text.all.*

class Profile:
  val empty = modifier()

  def apply() =
    RadioOptions(
      "radio-en",
      Language.English.code,
      "English",
      checked = false
    )

  case class RadioOptions(id: String, value: String, label: String, checked: Boolean)

  private def radios(groupName: String, rs: Seq[RadioOptions]) =
    div(`class` := "language-form")(rs.map: radio =>
      div(`class` := "form-check form-check-inline")(
        input(
          `class` := "form-check-input",
          `type` := "radio",
          name := groupName,
          id := radio.id,
          value := radio.value,
          if radio.checked then checked else empty
        ),
        label(`class` := "form-check-label", `for` := radio.id)(radio.label)
      ))
