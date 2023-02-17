package com.malliina.logstreams

package object http4s:
  implicit class EitherOps[L, R](val e: Either[L, R]) extends AnyVal:
    def recover[RR >: R](recover: L => RR): RR =
      e.fold(recover, identity)

    def recoverPF[RR >: R](pf: PartialFunction[L, RR]): Either[L, RR] =
      e.fold(l => if pf.isDefinedAt(l) then Right(pf(l)) else Left(l), r => Right(r))
