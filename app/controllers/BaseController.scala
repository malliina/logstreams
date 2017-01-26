package controllers

import com.malliina.play.http.Proxies
import play.api.http.Writeable
import play.api.mvc.{Action, Call, Controller, RequestHeader}

class BaseController extends Controller {
  def ws(call: Call, req: RequestHeader) =
    call.webSocketURL(Proxies.isSecure(req))(req)
}
