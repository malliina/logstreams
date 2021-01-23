package com.malliina.logstreams.auth

import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist}
import com.malliina.play.auth.BasicCredentials
import com.malliina.values.Username

trait UserService[F[_]] {
  def add(creds: BasicCredentials): F[Either[AlreadyExists, Unit]]
  def update(creds: BasicCredentials): F[Either[DoesNotExist, Unit]]
  def remove(user: Username): F[Either[DoesNotExist, Unit]]
  def isValid(creds: BasicCredentials): F[Boolean]
  def all(): F[Seq[Username]]
}

sealed trait UserError

object UserError {
  case class DoesNotExist(user: Username) extends UserError
  case class AlreadyExists(user: Username) extends UserError
}
