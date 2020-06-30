package com.malliina.logstreams.db

import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist}
import com.malliina.logstreams.auth.UserService
import com.malliina.logstreams.db.NewDatabaseAuth.log
import com.malliina.logstreams.db.NewStreamsDatabase.DatabaseContext
import com.malliina.play.auth.BasicCredentials
import com.malliina.values.{Password, Username}
import org.apache.commons.codec.digest.DigestUtils
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewDatabaseAuth {
  private val log = Logger(getClass)

  def hash(username: Username, password: Password): Password =
    Password(DigestUtils.md5Hex(username.name + ":" + password.pass))
}

class NewDatabaseAuth(val ctx: DatabaseContext)(implicit ec: ExecutionContext) extends UserService {
  import ctx._
  val users = quote(querySchema[DataUser]("USERS"))

  def add(creds: BasicCredentials): Future[Either[AlreadyExists, Unit]] = {
    val q = quote {
      users.insert(lift(DataUser(creds.username, hash(creds))))
    }
    run(q).map { rows =>
      if (rows > 0) {
        log.info(s"Added '${creds.username}'.")
      }
      Right(())
    }
  }
  def update(creds: BasicCredentials): Future[Either[DoesNotExist, Unit]] = {
    val q = quote {
      users
        .filter(_.user == lift(creds.username))
        .update(_.passHash -> lift(hash(creds)))
    }
    val a = runIO(q).map { rowCount =>
      if (rowCount > 0) {
        log.info(s"Updated the password of '${creds.username}'.")
        Right(())
      } else {
        Left(DoesNotExist(creds.username))
      }
    }
    performIO(a.transactional)
  }
  def remove(user: Username): Future[Either[DoesNotExist, Unit]] = {
    val q = quote(users.filter(_.user == lift(user)).delete)
    run(q).map { rowCount =>
      if (rowCount > 0) {
        log.info(s"Removed '$user'.")
        Right(())
      } else {
        Left(DoesNotExist(user))
      }
    }
  }
  def isValid(creds: BasicCredentials): Future[Boolean] = {
    val q = quote {
      users.filter(u => u.user == lift(creds.username) && u.passHash == lift(hash(creds))).nonEmpty
    }
    run(q)
  }
  def all(): Future[Seq[Username]] = {
    run(quote(users.map(_.user)))
  }

  private def hash(creds: BasicCredentials): Password =
    NewDatabaseAuth.hash(creds.username, creds.password)
}
