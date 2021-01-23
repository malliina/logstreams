package com.malliina.logstreams.auth

import cats.effect.IO
import com.malliina.play.auth.BasicCredentials
import com.malliina.values.Username

class PassThruAuth extends UserService[IO] {
  override def add(creds: BasicCredentials) = fut(Right(()))
  override def update(creds: BasicCredentials) = fut(Right(()))
  override def remove(user: Username) = fut(Right(()))
  override def isValid(token: BasicCredentials) = fut(true)
  override def all() = fut(Nil)

  def fut[T](code: => T): IO[T] = IO.pure(code)
}
