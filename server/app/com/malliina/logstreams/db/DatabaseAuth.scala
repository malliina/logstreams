package com.malliina.logstreams.db

import java.sql.SQLException

import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist}
import com.malliina.logstreams.auth.UserService
import com.malliina.logstreams.db.Mappings.{password, username}
import com.malliina.play.auth.BasicCredentials
import com.malliina.play.models.{Password, Username}
import org.apache.commons.codec.digest.DigestUtils
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

object DatabaseAuth {
  def apply(userDB: UserDB): DatabaseAuth = new DatabaseAuth(userDB)

  def hash(username: Username, password: Password): Password =
    Password(DigestUtils.md5Hex(username.name + ":" + password.pass))
}

class DatabaseAuth(db: UserDB) extends UserService {
  import UserDB.users
  implicit val ec = db.ec

  override def add(creds: BasicCredentials): Future[Either[AlreadyExists, Unit]] = {
    db.run(users += DataUser(creds.username, hash(creds))).map(_ => Right(())) recover {
      case sqle: SQLException if sqle.getMessage contains "primary key violation" =>
        Left(AlreadyExists(creds.username))
    }
  }

  override def update(creds: BasicCredentials): Future[Either[DoesNotExist, Unit]] =
    withUser(creds.username)(_.map(_.passHash).update(hash(creds)))

  override def remove(user: Username): Future[Either[DoesNotExist, Unit]] =
    withUser(user)(_.delete)

  override def isValid(creds: BasicCredentials): Future[Boolean] =
    db.run(userQuery(creds.username).filter(_.passHash === hash(creds)).exists.result)

  override def all(): Future[Seq[Username]] = db.runQuery(users.map(_.user))

  private def withUser[T](user: Username)(code: Query[Users, DataUser, Seq] => DBIOAction[Int, NoStream, _]) =
    db.run(code(userQuery(user))).map(c => analyzeRowCount(c, user))

  private def userQuery(user: Username): Query[Users, DataUser, Seq] = users.filter(_.user === user)

  private def analyzeRowCount(rowCount: Int, username: Username): Either[DoesNotExist, Unit] =
    if (rowCount == 0) Left(DoesNotExist(username)) else Right(())

  private def hash(creds: BasicCredentials) = DatabaseAuth.hash(creds.username, creds.password)
}
