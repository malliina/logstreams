package controllers

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import com.malliina.logstreams.tags.Htmls
import com.malliina.rx.BoundedReplaySubject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.WebSocket

import scala.concurrent.duration.DurationInt

class AkkaStreamCtrl(htmls: Htmls)(implicit mat: Materializer) extends BaseController {
  val ints = BoundedReplaySubject[Int](10).toSerialized
  val src = Source.queue[Int](10, OverflowStrategy.backpressure)
  val (q, p) = src.toMat(Sink.asPublisher(fanout = true))(Keep.both).run()

  def indexStreamed = okAction { req =>
    htmls.index
  }

  def webStream = WebSocket.accept[JsValue, JsValue] { req =>
    val (q, p) = src.toMat(Sink.asPublisher(fanout = true))(Keep.both).run()
    val timer = Source.tick(200.millis, 200.millis, () => (System.currentTimeMillis() / 1000).toInt)
    (1 to 10).foreach(i => q.offer(i))
    val streamSource: Source[Int, NotUsed] = Source.fromPublisher(p).merge(timer.map(f => f()))
    Flow.fromSinkAndSource(Sink.ignore, streamSource.map(i => Json.obj("value" -> i)))
  }

  def rxStream = WebSocket.accept[JsValue, JsValue] { req =>
    (1 to 10).foreach(i => ints.onNext(i))
    ints.subscribe(n => q.offer(n), e => q.offer(-1), () => q.offer(0))
    Flow.fromSinkAndSource(Sink.ignore, Source.fromPublisher(p).map(i => Json.obj("value" -> i)))
  }
}
