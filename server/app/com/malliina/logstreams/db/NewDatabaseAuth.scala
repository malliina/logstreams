package com.malliina.logstreams.db

import com.malliina.logstreams.auth.UserError.{AlreadyExists, DoesNotExist}
import com.malliina.logstreams.auth.UserService
import com.malliina.play.auth.BasicCredentials
import com.malliina.values.{Password, Username}
import com.zaxxer.hikari.HikariDataSource
import io.getquill.{MysqlEscape, MysqlJdbcContext, NamingStrategy, SnakeCase, UpperCase}
import org.apache.commons.codec.digest.DigestUtils
import play.api.Logger
import NewDatabaseAuth.log
import scala.concurrent.{ExecutionContext, Future}

object NewDatabaseAuth {
  private val log = Logger(getClass)

  def hash(username: Username, password: Password): Password =
    Password(DigestUtils.md5Hex(username.name + ":" + password.pass))

  def apply(ds: HikariDataSource, ec: ExecutionContext): NewDatabaseAuth =
    new NewDatabaseAuth(ds)(ec)
}

class NewDatabaseAuth(ds: HikariDataSource)(implicit ec: ExecutionContext) extends UserService {
  val naming = NamingStrategy(SnakeCase, UpperCase, MysqlEscape)
  lazy val ctx = new MysqlJdbcContext(naming, ds)
  import ctx._
  val users = quote(querySchema[DataUser]("USERS"))

  def add(creds: BasicCredentials): Future[Either[AlreadyExists, Unit]] = Future {
    val q = quote {
      users.insert(lift(DataUser(creds.username, hash(creds))))
    }
    val rows = run(q)
    if (rows > 0) {
      log.info(s"Added '${creds.username}'.")
    }
    Right(())
  }
  def update(creds: BasicCredentials): Future[Either[DoesNotExist, Unit]] = Future {
    transaction {
      val q = quote {
        users
          .filter(_.user == lift(creds.username))
          .update(_.passHash -> lift(hash(creds)))
      }
      val rowCount = run(q)
      if (rowCount > 0) {
        log.info(s"Updated the password of '${creds.username}'.")
        Right(())
      } else {
        Left(DoesNotExist(creds.username))
      }
    }
  }
  def remove(user: Username): Future[Either[DoesNotExist, Unit]] = Future {
    val q = quote(users.filter(_.user == lift(user)).delete)
    val rowCount = run(q)
    if (rowCount > 0) {
      log.info(s"Removed '$user'.")
      Right(())
    } else {
      Left(DoesNotExist(user))
    }
  }
  def isValid(creds: BasicCredentials): Future[Boolean] = Future {
    val q = quote {
      users.filter(u => u.user == lift(creds.username) && u.passHash == lift(hash(creds))).nonEmpty
    }
    run(q)
  }
  def all(): Future[Seq[Username]] = Future {
    run(quote(users.map(_.user)))
  }

  private def hash(creds: BasicCredentials): Password =
    NewDatabaseAuth.hash(creds.username, creds.password)
}
