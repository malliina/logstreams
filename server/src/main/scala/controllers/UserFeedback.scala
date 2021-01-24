package controllers

case class UserFeedback(message: String, isError: Boolean)

object UserFeedback {
  val Feedback = "feedback"
  val Success = "success"
  val Yes = "yes"
  val No = "no"

  def success(message: String) = UserFeedback(message, isError = false)
  def error(message: String) = UserFeedback(message, isError = true)
}
