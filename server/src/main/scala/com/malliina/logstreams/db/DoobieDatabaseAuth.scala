package com.malliina.logstreams.db

import cats.*
import cats.implicits.*
import com.malliina.database.DoobieDatabase
import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist}
import com.malliina.logstreams.auth.{BasicCredentials, UserService}
import com.malliina.logstreams.db.DoobieDatabaseAuth.log
import com.malliina.logstreams.models.AppName
import com.malliina.util.AppLogger
import com.malliina.values.{Password, Username}
import doobie.free.connection.ConnectionIO
import doobie.implicits.*

object DoobieDatabaseAuth:
  private val log = AppLogger(getClass)

class DoobieDatabaseAuth[F[_]](db: DoobieDatabase[F]) extends UserService[F]:
  def add(creds: BasicCredentials): F[Either[AlreadyExists, Unit]] = db.run:
    val existsIO = sql"select exists(select USER from USERS where USER = ${creds.username})"
      .query[Boolean]
      .unique
    existsIO.flatMap[Either[AlreadyExists, Unit]]: exists =>
      if exists then Left[AlreadyExists, Unit](AlreadyExists(creds.username)).pure[ConnectionIO]
      else
        val hashed = hash(creds)
        sql"""insert into USERS(USER, PASS_HASH) values (${creds.username}, $hashed)""".update.run
          .map: rows =>
            if rows > 0 then log.info(s"Inserted user '${creds.username}'.")
            Right(())

  def update(creds: BasicCredentials): F[Either[DoesNotExist, Unit]] = db.run:
    val user = creds.username
    val hashed = hash(creds)
    sql"""update USERS set PASS_HASH = $hashed where USER = $user""".update.run.map: rowCount =>
      if rowCount > 0 then
        log.info(s"Updated the password of '$user'.")
        Right(())
      else Left(DoesNotExist(user))

  def remove(user: Username): F[Either[DoesNotExist, Unit]] = db.run:
    sql"delete from USERS where USER = $user".update.run.map: rowCount =>
      if rowCount > 0 then
        log.info(s"Removed '$user'.")
        Right(())
      else Left(DoesNotExist(user))

  def isValid(creds: BasicCredentials): F[Boolean] = db.run:
    val hashed = hash(creds)
    sql"select exists(select USER from USERS where USER = ${creds.username} and PASS_HASH = $hashed)"
      .query[Boolean]
      .unique

  def exists(app: AppName): F[Boolean] = db.run:
    sql"select exists(select USER from USERS where USER = $app)"
      .query[Boolean]
      .unique

  def all(): F[List[Username]] = db.run:
    sql"select USER from USERS order by USER".query[Username].to[List]

  private def hash(creds: BasicCredentials): Password = Utils.hash(creds)
