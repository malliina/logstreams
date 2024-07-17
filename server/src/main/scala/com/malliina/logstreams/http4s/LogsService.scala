package com.malliina.logstreams.http4s

import cats.Applicative
import com.malliina.http4s.BasicService

class LogsService[F[_]: Applicative]
  extends BasicService[F]
  with Extractors
  with MyScalatagsInstances
