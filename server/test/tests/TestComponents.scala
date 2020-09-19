package tests

import com.malliina.app.{AppComponents, AppConf}
import com.malliina.logstreams.db.{NewDatabaseAuth, NewStreamsDatabase}
import play.api.ApplicationLoader.Context

import scala.concurrent.Future

class TestComponents(ctx: Context, conf: AppConf) extends AppComponents(ctx, _ => conf) {
  override lazy val auth = new TestAuth(controllerComponents.actionBuilder)
  val truncator = new Truncator(db, usersDb)
}

class Truncator(val db: NewStreamsDatabase, users: NewDatabaseAuth) {
  def truncate(): Future[Int] = {
    val step1 = {
      import db.ctx._
      db.transactionally("Truncate tables") {
        for {
          l <- runIO(db.logs.delete)
          t <- runIO(db.tokens.delete)
        } yield 42
      }
    }
    val step2: Long = {
      import users.ctx._
      users.ctx.run(users.users.delete)
    }
    step1
  }
}
