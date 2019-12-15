package tests

import com.malliina.app.{AppComponents, AppConf, EmbeddedMySQL}
import play.api.ApplicationLoader.Context

class TestComponents(ctx: Context, db: EmbeddedMySQL)
  extends AppComponents(ctx, _ => AppConf(EmbeddedMySQL.temporary)) {
  override lazy val auth = new TestAuth(controllerComponents.actionBuilder)
}
