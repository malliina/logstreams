package com.malliina.logstreams.auth

import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist, NoSuchEmail}
import com.malliina.logstreams.db.Admin
import com.malliina.logstreams.models.AppName
import com.malliina.values.{Email, Username}

trait UserService[F[_]]:
  def add(creds: BasicCredentials): F[Either[AlreadyExists, Unit]]
  def update(creds: BasicCredentials): F[Either[DoesNotExist, Unit]]
  def remove(user: Username): F[Either[DoesNotExist, Unit]]
  def isValid(creds: BasicCredentials): F[Boolean]
  def exists(app: AppName): F[Boolean]
  def all(): F[List[Username]]
  def admin(email: Email): F[Either[NoSuchEmail, Admin]]

sealed trait UserError

object UserError:
  case class DoesNotExist(user: Username) extends UserError
  case class AlreadyExists(user: Username) extends UserError
  case class NoSuchEmail(email: Email) extends UserError
