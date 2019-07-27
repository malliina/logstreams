package com.malliina.logstreams.db

import java.sql.SQLException

import com.malliina.concurrent.Execution.cached
import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist}
import com.malliina.logstreams.auth.UserService
import com.malliina.play.auth.BasicCredentials
import com.malliina.values.{Password, Username}
import org.apache.commons.codec.digest.DigestUtils

import scala.concurrent.Future

object DatabaseAuth {
  def apply(userDB: StreamsSchema): DatabaseAuth = new DatabaseAuth(userDB)

  def hash(username: Username, password: Password): Password =
    Password(DigestUtils.md5Hex(username.name + ":" + password.pass))
}

class DatabaseAuth(val db: StreamsSchema) extends UserService {
  import db.api._

  val users = db.users

  override def add(creds: BasicCredentials): Future[Either[AlreadyExists, Unit]] =
    db.run(users += DataUser(creds.username, hash(creds))).map(_ => Right(())) recover {
      case sqle: SQLException if sqle.getMessage contains "primary key violation" =>
        Left(AlreadyExists(creds.username))
    }

  override def update(creds: BasicCredentials): Future[Either[DoesNotExist, Unit]] =
    withUser(creds.username)(_.map(_.passHash).update(hash(creds)))

  override def remove(user: Username): Future[Either[DoesNotExist, Unit]] =
    withUser(user)(_.delete)

  override def isValid(creds: BasicCredentials): Future[Boolean] =
    db.run(userQuery(creds.username).filter(_.passHash === hash(creds)).exists.result)

  override def all(): Future[Seq[Username]] = db.runQuery(users.map(_.user))

  private def withUser[T](user: Username)(code: Query[db.Users, DataUser, Seq] => DBIOAction[Int, NoStream, _]) =
    db.run(code(userQuery(user))).map(c => analyzeRowCount(c, user))

  private def userQuery(user: Username): Query[db.Users, DataUser, Seq] = users.filter(_.user === user)

  private def analyzeRowCount(rowCount: Int, username: Username): Either[DoesNotExist, Unit] =
    if (rowCount == 0) Left(DoesNotExist(username)) else Right(())

  private def hash(creds: BasicCredentials) = DatabaseAuth.hash(creds.username, creds.password)
}
