package com.malliina.logstreams.db

import java.sql.SQLException

import com.malliina.logstreams.db.DatabaseLike.log
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.jdbc.H2Profile.api._
import slick.jdbc.meta.MTable
import slick.lifted.{AbstractTable, TableQuery}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.higherKinds

trait DatabaseLike {
  def database: Database

  def tableQueries: Seq[TableQuery[_ <: Table[_]]]

  def runQuery[A, B, C[_]](query: Query[A, B, C]): Future[C[B]] = run(query.result)

  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = database.run(a)

  def init(): Unit = {
    log info s"Ensuring all tables exist..."
    createIfNotExists(tableQueries: _*)
  }

  def exists[T <: AbstractTable[_]](table: TableQuery[T]): Boolean = {
    val tableName = table.baseTableRow.tableName
    try {
      val future = database.run(MTable.getTables(tableName))
      await(future).nonEmpty
    } catch {
      case sqle: SQLException =>
        log.error(s"Unable to verify table: $tableName", sqle)
        false
    }
  }

  def createIfNotExists[T <: Table[_]](tables: TableQuery[T]*): Unit =
    tables.reverse.filter(t => !exists(t)).foreach(t => initTable(t))

  def initTable[T <: Table[_]](table: TableQuery[T]) = {
    await(database.run(table.schema.create))
    log info s"Created table: ${table.baseTableRow.tableName}"
  }

  def executePlain(queries: DBIOAction[Int, NoStream, Nothing]*): Future[Seq[Int]] =
    sequentially(queries.toList)

  def sequentially(queries: List[DBIOAction[Int, NoStream, Nothing]]): Future[List[Int]] =
    queries match {
      case head :: tail =>
        database.run(head).flatMap(i => sequentially(tail).map(is => i :: is))
      case Nil =>
        Future.successful(Nil)
    }

  private def await[T](f: Future[T]): T = Await.result(f, 10.seconds)
}

object DatabaseLike {
  private val log = Logger(getClass)
}
