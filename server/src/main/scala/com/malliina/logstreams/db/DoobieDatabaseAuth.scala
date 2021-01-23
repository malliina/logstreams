package com.malliina.logstreams.db

import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist}
import com.malliina.logstreams.auth.UserService
import com.malliina.logstreams.db.DoobieDatabaseAuth.log
import com.malliina.play.auth.BasicCredentials
import com.malliina.values.{Password, Username}
import doobie.implicits._
import play.api.Logger
import cats.effect.IO
import scala.concurrent.Future

object DoobieDatabaseAuth {
  private val log = Logger(getClass)

  def apply(db: DoobieDatabase): DoobieDatabaseAuth = new DoobieDatabaseAuth(db)
}

class DoobieDatabaseAuth(db: DoobieDatabase) extends UserService[IO] {
  def add(creds: BasicCredentials): IO[Either[AlreadyExists, Unit]] = db.run {
    val existsIO = sql"select exists(select USER from USERS where USER = ${creds.username})"
      .query[Boolean]
      .unique
    existsIO.flatMap[Either[AlreadyExists, Unit]] { exists =>
      if (exists) {
        AsyncConnectionIO.pure[Either[AlreadyExists, Unit]](Left(AlreadyExists(creds.username)))
      } else {
        val hashed = hash(creds)
        sql"""insert into USERS(USER, PASS_HASH) values (${creds.username}, $hashed)""".update.run
          .map { _ =>
            Right(())
          }
      }
    }

  }

  def update(creds: BasicCredentials): IO[Either[DoesNotExist, Unit]] = db.run {
    val user = creds.username
    val hashed = hash(creds)
    sql"""update USERS set PASS_HASH = $hashed where USER = $user""".update.run.map { rowCount =>
      if (rowCount > 0) {
        log.info(s"Updated the password of '$user'.")
        Right(())
      } else {
        Left(DoesNotExist(user))
      }
    }
  }

  def remove(user: Username): IO[Either[DoesNotExist, Unit]] = db.run {
    sql"delete from USERS where USER = ${user}".update.run.map { rowCount =>
      if (rowCount > 0) {
        log.info(s"Removed '$user'.")
        Right(())
      } else {
        Left(DoesNotExist(user))
      }
    }
  }

  def isValid(creds: BasicCredentials) = db.run {
    val hashed = hash(creds)
    sql"select exists(select USER from USERS where USER = ${creds.username} and PASS_HASH = $hashed)"
      .query[Boolean]
      .unique
  }

  def all(): IO[Seq[Username]] = db.run {
    sql"select USER from USERS".query[Username].to[List]
  }

  private def hash(creds: BasicCredentials): Password = Utils.hash(creds)
}
