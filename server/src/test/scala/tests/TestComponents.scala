package tests

import cats.effect.IO
import com.malliina.logstreams.db.DoobieDatabase
import doobie.util.fragment.Fragment

//class TestComponents(ctx: Context, conf: AppConf) extends AppComponents(ctx, _ => conf) {
//  override lazy val auth: LogAuth = new TestAuth(controllerComponents.actionBuilder)
//  val truncator = new Truncator(doobieDb)
//}

class Truncator(db: DoobieDatabase):
  def truncate(): IO[Int] =
    import cats.implicits.*
    import doobie.implicits.*
    val io = List("LOGS", "TOKENS", "USERS").traverse { tableName =>
      val table = Fragment.const0(tableName)
      sql"delete from $table".update(db.logHandler).run
    }
      .map(_.sum)
    db.run(io)
