package tests

import com.malliina.app.{AppComponents, AppConf}
import com.malliina.logstreams.db.DoobieDatabase
import controllers.LogAuth
import doobie.util.fragment.Fragment
import play.api.ApplicationLoader.Context

import scala.concurrent.Future

class TestComponents(ctx: Context, conf: AppConf) extends AppComponents(ctx, _ => conf) {
  override lazy val auth: LogAuth = new TestAuth(controllerComponents.actionBuilder)
  val truncator = new Truncator(doobieDb)
}

class Truncator(db: DoobieDatabase) {
  def truncate(): Future[Int] = {
    import doobie.implicits._
    import cats.implicits._
    implicit val ec = db.ec
    val io = List("LOGS", "TOKENS", "USERS")
      .traverse { tableName =>
        val table = Fragment.const0(tableName)
        sql"delete from $table".update(db.logHandler).run
      }
      .map(_.sum)
    db.run(io)
  }
}
