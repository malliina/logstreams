package controllers

import akka.stream.Materializer
import com.malliina.logstreams.auth.UserService
import com.malliina.play.auth.BasicCredentials
import com.malliina.play.controllers.BaseSecurity

import scala.concurrent.Future

class Auth(mat: Materializer, users: UserService) extends BaseSecurity(mat) {
  override def validateCredentials(creds: BasicCredentials): Future[Boolean] =
    users isValid creds
}
