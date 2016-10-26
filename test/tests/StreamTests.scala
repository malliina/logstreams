package tests

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.malliina.rx.BoundedReplaySubject
import org.scalatest.FunSuite

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class StreamTests extends FunSuite {
  implicit val mat = ActorMaterializer()(ActorSystem("test"))
  implicit val ec = mat.executionContext

  test("actors") {
    val source = Source.actorRef[Int](1000, OverflowStrategy.dropHead)
    val printer = Sink.foreach(println)
    val ref = source.toMat(printer)(Keep.left).run()
    ref ! 42
  }

  test("akka streams") {
    val (queue, publisher) = Source.queue[Int](2, OverflowStrategy.backpressure)
      .toMat(Sink.asPublisher(fanout = true))(Keep.both).run()
    val source = Source.fromPublisher(publisher)
    val sink = Flow[Int].mapAsync(1)(i => queue.offer(i).map(r => println(r))).to(Sink.foreach(println))
    await(Future.sequence((1 to 5).map(i => queue.offer(i).map(r => println(r)))))
    source.runForeach(println)
  }

  test("subjects") {
    val s = BoundedReplaySubject[Int](3).toSerialized
    s.onNext(1)
    s.onNext(2)
    s.onNext(3)
    s.onNext(4)
    s.subscribe(n => println(s"First: $n"))
    s.onNext(5)
    s.subscribe(n => println(s"Second: $n"))
    Thread.sleep(100)
    s.onNext(6)
  }

  def await[T](f: Future[T]) = Await.result(f, 5.seconds)
}
