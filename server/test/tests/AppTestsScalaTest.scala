package tests

import ch.vorburger.mariadb4j.{DB, DBConfigurationBuilder}
import com.malliina.app.AppComponents
import com.malliina.logstreams.db.Conf
import play.api.ApplicationLoader.Context
import play.api.test.FakeRequest
import play.api.test.Helpers._

class TestComponents(ctx: Context) extends AppComponents(ctx, _ => TestComps.startTestDatabase()) {
  override lazy val auth = new TestAuth(controllerComponents.actionBuilder)
}

class TestSuite extends AppSuite(new TestComponents(_))

class AppTestsScalaTest extends TestSuite {

  test("can make request") {
    val result = route(app, FakeRequest(GET, "/")).get
    assert(status(result) === 200)
  }
}

object TestComps {
  def startTestDatabase(): Conf = {
    val dbConfig = DBConfigurationBuilder.newBuilder()
    val db = DB.newEmbeddedDB(dbConfig.build())
    db.start()
    Conf(dbConfig.getURL("test"), "root", "", Conf.MySQLDriver)
  }
}
