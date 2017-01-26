package tests

import com.malliina.app.AppComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._

class TestSuite extends AppSuite(new AppComponents(_))

class AppTestsScalaTest extends TestSuite {

  test("can make request") {
    val result = route(app, FakeRequest(GET, "/")).get
    assert(status(result) === 200)
  }
}
