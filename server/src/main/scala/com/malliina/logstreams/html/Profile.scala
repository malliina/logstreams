package com.malliina.logstreams.html

import com.malliina.http.{CSRFConf, CSRFToken}
import com.malliina.logstreams.html.Htmls.given
import com.malliina.logstreams.http4s.UserFeedback
import com.malliina.logstreams.models.{Lang, Language}
import scalatags.Text.all.*

class Profile(csrfConf: CSRFConf) extends BaseHtml:
  def apply(language: Language, token: CSRFToken, lang: Lang, feedback: Option[UserFeedback]) =
    val plang = lang.profile
    div(
      headerRow(plang.title),
      fullRow(feedback.fold(empty)(feedbackDiv)),
      radios(
        LanguageKey,
        Seq(
          RadioOptions(
            "radio-se",
            Language.Swedish.code,
            plang.swedish,
            checked = language == Language.Swedish
          ),
          RadioOptions(
            "radio-en",
            Language.English.code,
            plang.english,
            checked = language == Language.English
          )
        ),
        token
      )
    )

  case class RadioOptions(id: String, value: String, label: String, checked: Boolean)

  private def radios(groupName: String, rs: Seq[RadioOptions], token: CSRFToken) =
    form(cls := "language-form", id := LanguageForm, action := "/profile", method := "post")(
      modifier(
        input(tpe := "hidden", name := csrfConf.tokenName, value := token),
        rs.map: radio =>
          div(cls := "form-check form-check-inline")(
            input(
              cls := "form-check-input",
              tpe := "radio",
              name := groupName,
              id := radio.id,
              value := radio.value,
              if radio.checked then checked else empty
            ),
            label(cls := "form-check-label", `for` := radio.id)(radio.label)
          )
      )
    )
