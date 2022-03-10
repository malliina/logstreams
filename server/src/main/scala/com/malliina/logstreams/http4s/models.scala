package com.malliina.logstreams.http4s

import com.malliina.values.Username
import org.http4s.Headers

import java.time.OffsetDateTime

case class UserFeedback(message: String, isError: Boolean)

object UserFeedback:
  val Feedback = "feedback"
  val Success = "success"
  val Yes = "yes"
  val No = "no"

  def success(message: String) = UserFeedback(message, isError = false)
  def error(message: String) = UserFeedback(message, isError = true)

case class UserRequest(user: Username, headers: Headers, address: String, now: OffsetDateTime)
