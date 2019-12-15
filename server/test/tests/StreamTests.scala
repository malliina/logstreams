package tests

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.scalatest.FunSuite

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class StreamTests extends FunSuite {
  implicit val as = ActorSystem("test")
  implicit val ec = as.dispatcher

  ignore("akka streams") {
    val (queue, publisher) = Source
      .queue[Int](2, OverflowStrategy.backpressure)
      .toMat(Sink.asPublisher(fanout = true))(Keep.both)
      .run()
    val source = Source.fromPublisher(publisher)
    val sink =
      Flow[Int].mapAsync(1)(i => queue.offer(i).map(r => println(r))).to(Sink.foreach(println))
    await(Future.sequence((1 to 5).map(i => queue.offer(i).map(r => println(r)))))
    source.runForeach(println)
  }

  def await[T](f: Future[T]) = Await.result(f, 5.seconds)
}
