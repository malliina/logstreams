package tests

import com.malliina.app.{AppComponents, AppConf}
import play.api.ApplicationLoader.Context

class TestComponents(ctx: Context, conf: AppConf) extends AppComponents(ctx, _ => conf) {
  override lazy val auth = new TestAuth(controllerComponents.actionBuilder)
}
