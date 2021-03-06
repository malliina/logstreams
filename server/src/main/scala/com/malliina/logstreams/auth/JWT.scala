package com.malliina.logstreams.auth

import java.text.ParseException
import java.time.Instant
import java.util.Date

import com.malliina.util.AppLogger
import com.malliina.values.{ErrorMessage, IdToken, TokenValue}
import com.malliina.web.{
  Expired,
  InvalidClaims,
  InvalidSignature,
  Issuer,
  JWTError,
  MissingData,
  ParseError,
  Readable
}
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import play.api.libs.json._

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.Try

object JWT {
  private val log = AppLogger(getClass)

  def apply(secret: SecretKey): JWT = new JWT(secret)

  case class Parsed(
    token: TokenValue,
    jwt: SignedJWT,
    claims: JWTClaimsSet,
    claimsJson: JsValue
  ) {
    val exp = Option(claims.getExpirationTime).map(_.toInstant)
    val iss = Option(claims.getIssuer).map(Issuer.apply)

    // No expiration => never expired
    def checkExpiration(now: Instant) = exp.flatMap { e =>
      if (now.isBefore(e)) None else Option(Expired(token, e, now))
    }

    def verify(secret: SecretKey, now: Instant): Either[JWTError, Verified] = {
      val verifier = new MACVerifier(secret.value)
      for {
        _ <- if (jwt.verify(verifier)) Right(()) else Left(InvalidSignature(token))
        _ <- checkExpiration(now).toLeft(())
      } yield Verified(this)
    }

    def readString(key: String): Either[JWTError, String] =
      read(token, claims.getStringClaim(key), s"Claim missing: '$key'.")
  }

  object Parsed {
    def apply(token: TokenValue): Either[JWTError, Parsed] =
      for {
        signed <- read(token, SignedJWT.parse(token.value), s"Invalid JWT: '$token'.")
        claims <- read(token, signed.getJWTClaimsSet, s"Missing claims: '$token'.")
        json <- Try(Json.parse(claims.toString)).toEither.left.map(_ =>
          InvalidClaims(token, ErrorMessage("Claims must be JSON."))
        )
      } yield Parsed(token, signed, claims, json)
  }

  case class Verified private (parsed: Parsed) {
    def expiresIn(now: Instant): Option[FiniteDuration] = parsed.exp.map { exp =>
      (exp.toEpochMilli - now.toEpochMilli).millis
    }
    def readString(key: String) = parsed.readString(key)
    def token = parsed.token
    def read[T: Reads](key: String): Either[JWTError, T] =
      (parsed.claimsJson \ key).validate[T].asEither.left.map { errors =>
        InvalidClaims(token, ErrorMessage(s"Invalid claims: '$errors'."))
      }
    def parse[T](key: String)(implicit r: Readable[T]): Either[JWTError, T] =
      readString(key).flatMap { s =>
        r.read(s).left.map(err => InvalidClaims(token, err))
      }
  }

  private def read[T](token: TokenValue, f: => T, onMissing: => String): Either[JWTError, T] =
    try {
      Option(f).toRight(MissingData(token, ErrorMessage(onMissing)))
    } catch {
      case pe: ParseException =>
        log.error(s"Parse error for token '$token'.", pe)
        Left(ParseError(token, pe))
    }
}

class JWT(secret: SecretKey, dataKey: String = "data") {
  def sign[T: Writes](payload: T, ttl: FiniteDuration, now: Instant = Instant.now()): IdToken =
    signWithExpiration[T](payload, now.plusSeconds(ttl.toSeconds))

  def signWithExpiration[T: Writes](payload: T, expiresAt: Instant): IdToken = {
    val signer = new MACSigner(secret.value)
    val claims = new JWTClaimsSet.Builder()
      .expirationTime(Date.from(expiresAt))
      .claim(dataKey, JSONObjectUtils.parse(Json.stringify(Json.toJson(payload))))
      .build()
    val signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims)
    signed.sign(signer)
    IdToken(signed.serialize())
  }

  def verify[T: Reads](token: TokenValue, now: Instant = Instant.now()): Either[JWTError, T] =
    verifyToken(token, now).flatMap { v =>
      v.read[T](dataKey)
    }

  def verifyToken(token: TokenValue, now: Instant): Either[JWTError, JWT.Verified] =
    JWT.Parsed(token).flatMap { p =>
      p.verify(secret, now)
    }
}
