package com.malliina.logstreams.auth

import com.malliina.play.auth.{Auth, InvalidCredentials, UserAuthenticator}
import controllers.LogAuth

import scala.concurrent.{ExecutionContext, Future}

object Auths {
  def sources(users: UserService)(implicit ec: ExecutionContext): UserAuthenticator =
    UserAuthenticator { rh =>
      def fail = Left(InvalidCredentials(rh))

      Auth.basicCredentials(rh)
        .map(creds => users.isValid(creds).map(isValid => if (isValid) Right(creds.username) else fail))
        .getOrElse(Future.successful(fail))
    }

  def viewers(auth: LogAuth)(implicit ec: ExecutionContext): UserAuthenticator =
    UserAuthenticator(auth.authenticateSocket)
}
