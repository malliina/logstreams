package com.malliina.logstreams

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}

object Streams extends Streams

trait Streams {
  def actorRef(bufferSize: Int, overflowStrategy: OverflowStrategy) = Source.actorRef(
    { case _: Any => CompletionStrategy.draining },
    { case elem => new Exception(s"Stream failed. $elem") },
    bufferSize,
    overflowStrategy
  )

  /** The publisher-dance makes it so that even with multiple subscribers, `once` only runs once. Without this wrapping,
    * `once` executes independently for each subscriber, which is undesired if `once` involves a side-effect
    * (e.g. a database insert operation).
    *
    * @param once source to only run once for each emitted element
    * @tparam T type of element
    * @tparam U materialized value
    * @return a Source that supports multiple subscribers, but does not independently run `once` for each
    */
  def onlyOnce[T, U](once: Source[T, U])(implicit mat: Materializer) =
    Source.fromPublisher(once.runWith(Sink.asPublisher(fanout = true)))

  def rights[L, R](src: Source[Either[L, R], NotUsed]): Source[R, NotUsed] =
    src.flatMapConcat(e => e.fold(_ => Source.empty, c => Source.single(c)))

  def lefts[L, R](src: Source[Either[L, R], NotUsed]): Source[L, NotUsed] =
    src.flatMapConcat(e => e.fold(err => Source.single(err), _ => Source.empty))
}
