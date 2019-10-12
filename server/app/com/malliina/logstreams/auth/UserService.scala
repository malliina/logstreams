package com.malliina.logstreams.auth

import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist}
import com.malliina.play.auth.BasicCredentials
import com.malliina.values.Username

import scala.concurrent.Future

trait UserService {
  def add(creds: BasicCredentials): Future[Either[AlreadyExists, Unit]]
  def update(creds: BasicCredentials): Future[Either[DoesNotExist, Unit]]
  def remove(user: Username): Future[Either[DoesNotExist, Unit]]
  def isValid(creds: BasicCredentials): Future[Boolean]
  def all(): Future[Seq[Username]]
}

sealed trait UserError

object UserError {
  case class DoesNotExist(user: Username) extends UserError
  case class AlreadyExists(user: Username) extends UserError
}
