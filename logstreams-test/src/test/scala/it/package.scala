import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

package object it:
  def await[T](f: Future[T]): T = Await.result(f, 100.seconds)
