package controllers

import play.api.mvc.{Action, Controller, RequestHeader}
import play.twirl.api.Html

class BaseController extends Controller {
  def okAction(f: RequestHeader => Html) = Action(req => Ok(f(req)))
}
