package tests

import com.malliina.app.AppComponents
import play.api.ApplicationLoader.Context
import play.api.test.FakeRequest
import play.api.test.Helpers._

class TestComponents(ctx: Context) extends AppComponents(ctx) {
  override lazy val auth = new TestAuth(controllerComponents.actionBuilder)
}

class TestSuite extends AppSuite(new TestComponents(_))

class AppTestsScalaTest extends TestSuite {

  test("can make request") {
    val result = route(app, FakeRequest(GET, "/")).get
    assert(status(result) === 200)
  }
}
