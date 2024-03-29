package com.malliina.logstreams.http4s

import com.malliina.http.FullUrl
import org.http4s.Request
import org.typelevel.ci.CIStringSyntax

object Urls:
  def hostOnly[F[_]](req: Request[F]): FullUrl =
    val proto = if isSecure(req) then "https" else "http"
    val uri = req.uri
    val hostAndPort =
      req.headers
        .get(ci"X-Forwarded-Host")
        .map(_.head.value)
        .orElse(uri.host.map(_.value))
        .orElse(req.headers.get(ci"Host").map(_.head.value))
        .getOrElse("localhost")
    FullUrl(proto, hostAndPort, "")

  def topDomainFrom(req: Request[?]): String = topDomain(hostOnly(req).host)

  def topDomain(in: String): String =
    in.split('.').takeRight(2).mkString(".").takeWhile(c => c != ':' && c != '/')

  def isSecure[F[_]](req: Request[F]): Boolean =
    req.isSecure.getOrElse(false) || req.headers
      .get(ci"X-Forwarded-Proto")
      .exists(_.exists(_.value == "https"))

  def address[F[_]](req: Request[F]): String =
    req.headers
      .get(ci"X-Forwarded-For")
      .map(_.head.value)
      .orElse(req.remoteAddr.map(_.toUriString))
      .getOrElse("unknown")
