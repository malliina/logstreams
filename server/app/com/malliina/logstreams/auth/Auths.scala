package com.malliina.logstreams.auth

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.play.auth.{Auth, InvalidCredentials, UserAuthenticator}
import controllers.LogAuth

import scala.concurrent.Future

object Auths {
  def sources(users: UserService): UserAuthenticator =
    UserAuthenticator { rh =>
      def fail = Left(InvalidCredentials(rh))

      Auth.basicCredentials(rh)
        .map(creds => users.isValid(creds).map(isValid => if (isValid) Right(creds.username) else fail))
        .getOrElse(Future.successful(fail))
    }

  def viewers(auth: LogAuth): UserAuthenticator =
    UserAuthenticator(rh => auth.authenticateSocket(rh).map(_.right.map(_.user)))
}
