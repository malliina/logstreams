package com.malliina.logstreams.auth

import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist}
import com.malliina.play.auth.BasicCredentials
import com.malliina.play.models.Username

import scala.concurrent.Future

trait UserService {
  def add(creds: BasicCredentials): Future[Either[AlreadyExists, Unit]]

  def update(creds: BasicCredentials): Future[Either[DoesNotExist, Unit]]

  def remove(user: Username): Future[Either[DoesNotExist, Unit]]

  def isValid(token: BasicCredentials): Future[Boolean]

  def all(): Future[Seq[BasicCredentials]]
}

sealed trait UserError

object UserError {

  case class DoesNotExist(user: Username) extends UserError

  case class AlreadyExists(user: Username) extends UserError

}
