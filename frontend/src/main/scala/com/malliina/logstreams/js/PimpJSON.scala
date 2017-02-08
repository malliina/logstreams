package com.malliina.logstreams.js

import upickle.{Invalid, Js}


object PimpJSON extends upickle.AttributeTagged {
  import Aliases.RW
  // By default, upickle refuses to read Numbers to Long.
  // https://github.com/lihaoyi/upickle-pprint/issues/66
  // So we roll our own...
  override implicit val LongRW = NumericReadWriter[Long](_.toLong, s => java.lang.Long.parseLong(s))

  def NumericReadWriter[T: Numeric](func: Double => T, func2: String => T): RW[T] = RW[T](
    x => Js.Num(implicitly[Numeric[T]].toDouble(x)),
    numericReaderFunc[T](func, func2)
  )

  def numericReaderFunc[T: Numeric](func: Double => T, func2: String => T): PartialFunction[Js.Value, T] = Internal.validate("Number or String"){
    case n @ Js.Num(x) => try{func(x) } catch {case e: NumberFormatException => throw Invalid.Data(n, "Number")}
    case s @ Js.Str(x) => try{func2(x) } catch {case e: NumberFormatException => throw Invalid.Data(s, "Number")}
  }

  // By default, upickle writes Options as JSON arrays. This is undesirable.
  // This override writes empty Optional values as null.
  override implicit def OptionW[T: Writer]: Writer[Option[T]] = Writer {
    case None => Js.Null
    case Some(s) => implicitly[Writer[T]].write(s)
  }

  override implicit def OptionR[T: Reader]: Reader[Option[T]] = Reader {
    case Js.Null => None
    case v: Js.Value => Some(implicitly[Reader[T]].read.apply(v))
  }

  def validate[T: Reader](expr: String): Either[Invalid, T] =
    toEither[T] {
      val jsValue = read[Js.Value](expr)
      readJs[T](jsValue)
    }


  def validateJs[T: Reader](v: Js.Value): Either[Invalid, T] =
    toEither(readJs[T](v))

  def toEither[T](code: => T): Either[Invalid, T] =
    try {
      Right(code)
    } catch {
      case e: Invalid => Left(e)
    }
}
