package com.malliina.logstreams.auth

import com.malliina.logstreams.auth.JWT.Verified

import java.text.ParseException
import java.time.Instant
import java.util.Date
import com.malliina.util.AppLogger
import com.malliina.values.{ErrorMessage, IdToken, TokenValue, Readable}
import com.malliina.web.{Expired, InvalidClaims, InvalidSignature, Issuer, JWTError, MissingData, ParseError}
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import io.circe.{Decoder, Encoder, Json}
import io.circe.parser.parse
import io.circe.syntax.*

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.Try

object JWT:
  private val log = AppLogger(getClass)

  case class Parsed(
    token: TokenValue,
    jwt: SignedJWT,
    claims: JWTClaimsSet,
    claimsJson: Json
  ):
    val exp = Option(claims.getExpirationTime).map(_.toInstant)
    val iss = Option(claims.getIssuer).map(Issuer.apply)

    def readString(key: String): Either[JWTError, String] =
      read(token, claims.getStringClaim(key), s"Claim missing: '$key'.")

  object Parsed:
    def apply(token: TokenValue): Either[JWTError, Parsed] =
      for
        signed <- read(token, SignedJWT.parse(token.value), s"Invalid JWT: '$token'.")
        claims <- read(token, signed.getJWTClaimsSet, s"Missing claims: '$token'.")
        json <- parse(claims.toString).left.map(_ =>
          InvalidClaims(token, ErrorMessage("Claims must be JSON."))
        )
      yield Parsed(token, signed, claims, json)

  case class Verified private (parsed: Parsed):
    def expiresIn(now: Instant): Option[FiniteDuration] = parsed.exp.map { exp =>
      (exp.toEpochMilli - now.toEpochMilli).millis
    }
    def readString(key: String) = parsed.readString(key)
    def token = parsed.token
    def read[T: Decoder](key: String): Either[JWTError, T] =
      parsed.claimsJson.hcursor.downField(key).as[T].left.map { errors =>
        InvalidClaims(token, ErrorMessage(s"Invalid claims: '$errors'."))
      }
    def parse[T](key: String)(implicit r: Readable[T]): Either[JWTError, T] =
      readString(key).flatMap { s =>
        r.read(s).left.map(err => InvalidClaims(token, err))
      }

  object Verified:
    // No expiration => never expired
    def checkExpiration(parsed: Parsed, now: Instant) = parsed.exp.flatMap { e =>
      if now.isBefore(e) then None else Option(Expired(parsed.token, e, now))
    }

    def verify(parsed: Parsed, secret: SecretKey, now: Instant): Either[JWTError, Verified] =
      val verifier = new MACVerifier(secret.value)
      for
        _ <- if parsed.jwt.verify(verifier) then Right(()) else Left(InvalidSignature(parsed.token))
        _ <- checkExpiration(parsed, now).toLeft(())
      yield Verified(parsed)

  private def read[T](token: TokenValue, f: => T, onMissing: => String): Either[JWTError, T] =
    try Option(f).toRight(MissingData(token, ErrorMessage(onMissing)))
    catch
      case pe: ParseException =>
        log.error(s"Parse error for token '$token'.", pe)
        Left(ParseError(token, pe))

class JWT(secret: SecretKey, dataKey: String = "data"):
  def sign[T: Encoder](payload: T, ttl: FiniteDuration, now: Instant = Instant.now()): IdToken =
    signWithExpiration[T](payload, now.plusSeconds(ttl.toSeconds))

  def signWithExpiration[T: Encoder](payload: T, expiresAt: Instant): IdToken =
    val signer = new MACSigner(secret.value)
    val claims = new JWTClaimsSet.Builder()
      .expirationTime(Date.from(expiresAt))
      .claim(dataKey, JSONObjectUtils.parse(payload.asJson.noSpaces))
      .build()
    val signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims)
    signed.sign(signer)
    IdToken(signed.serialize())

  def verify[T: Decoder](token: TokenValue, now: Instant = Instant.now()): Either[JWTError, T] =
    verifyToken(token, now).flatMap { v =>
      v.read[T](dataKey)
    }

  def verifyToken(token: TokenValue, now: Instant): Either[JWTError, JWT.Verified] =
    JWT.Parsed(token).flatMap { p =>
      Verified.verify(p, secret, now)
    }
