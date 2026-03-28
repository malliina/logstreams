package com.malliina.logstreams.html

import com.malliina.html.{Bootstrap, HtmlTags}
import com.malliina.logstreams.http4s.UserFeedback
import com.malliina.logstreams.models.FrontStrings
import scalatags.Text.all.modifier

class BaseHtml extends Bootstrap(HtmlTags) with FrontStrings:
  val empty = modifier()

  def feedbackDiv(feedback: UserFeedback) =
    val message = feedback.message
    if feedback.isError then alertDanger(message)
    else alertSuccess(message)
