package tests

import com.malliina.app.AppComponents
import controllers.OAuthRoutes
import play.api.ApplicationLoader.Context
import play.api.mvc.{Action, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._

object TestOAuthRoutes extends OAuthRoutes {
  override def initiate = ok

  override def redirResponse = ok

  def ok = Action(Results.Ok)
}

class TestComponents(ctx: Context) extends AppComponents(ctx) {
  override lazy val auth = new TestAuth
  override lazy val oauth = TestOAuthRoutes
}

class TestSuite extends AppSuite(new TestComponents(_))

class AppTestsScalaTest extends TestSuite {

  test("can make request") {
    val result = route(app, FakeRequest(GET, "/")).get
    assert(status(result) === 200)
  }
}
