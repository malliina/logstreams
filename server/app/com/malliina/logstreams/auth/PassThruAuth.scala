package com.malliina.logstreams.auth

import com.malliina.play.auth.BasicCredentials
import com.malliina.values.Username

import scala.concurrent.Future

class PassThruAuth extends UserService {
  override def add(creds: BasicCredentials) = fut(Right(()))
  override def update(creds: BasicCredentials) = fut(Right(()))
  override def remove(user: Username) = fut(Right(()))
  override def isValid(token: BasicCredentials) = fut(true)
  override def all() = fut(Nil)

  def fut[T](code: => T): Future[T] = Future.successful(code)
}
