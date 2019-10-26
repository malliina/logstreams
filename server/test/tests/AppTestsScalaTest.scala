package tests

import ch.vorburger.mariadb4j.{DB, DBConfigurationBuilder}
import com.malliina.app.AppComponents
import com.malliina.logstreams.db.Conf
import play.api.ApplicationLoader.Context
import play.api.test.FakeRequest
import play.api.test.Helpers._

class TestComponents(ctx: Context, db: DB)
    extends AppComponents(ctx, _ => TestComps.startTestDatabase(db)) {
  override lazy val auth = new TestAuth(controllerComponents.actionBuilder)
}

object TestComps {
  lazy val db: DB = startDB()

  private def startDB(): DB = {
    val dbConfig = DBConfigurationBuilder.newBuilder()
    val db = DB.newEmbeddedDB(dbConfig.build())
    db.start()
    db
  }

  def startTestDatabase(embedded: DB): Conf = {
    Conf(embedded.getConfiguration.getURL("test"), "root", "", Conf.MySQLDriver)
  }
}
