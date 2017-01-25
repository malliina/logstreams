package com.malliina.logstreams.auth

import scala.concurrent.Future

trait TokenService {
  def add(creds: Creds): Future[Unit]

  def remove(creds: Creds): Future[Unit]

  def exists(creds: Creds): Future[Boolean]

  def all(): Future[Seq[Creds]]
}
