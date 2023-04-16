package com.example.auth

import com.example.AuthConfig
import com.example.db.UserInfo
import org.json4s.{ DefaultFormats, Formats }
import org.json4s.ext.JavaTypesSerializers
import pdi.jwt.{ Jwt, JwtAlgorithm, JwtBase64, JwtClaim, JwtHeader }

import java.security.{ MessageDigest, PrivateKey }
import java.time.Clock
import scala.util.Try

case class TokenCreator(config: AuthConfig) {

  import org.json4s.native.Serialization._
  implicit val formats: Formats = DefaultFormats ++ JavaTypesSerializers.all

  val algorithm     = JwtAlgorithm.fromString("RS256")
  val defaultHeader = JwtHeader(algorithm = Some(algorithm))

  def encode(header: JwtHeader, claims: JwtClaim): Try[String] = Try {
    Jwt.encode(header, claims, config.privateKey)
  }

  def createTokenPair(userInfo: UserInfo, sessionId: String): Try[TokenPair] = {
    val accessClaims = AccessClaims(sessionId)
    val idClaims     = IdClaims.from(userInfo)
    for {
      accessToken        <- createAccessToken(accessClaims)
      at_hash            <- createFieldHash(accessToken.rawToken, algorithm)
      idClaimsWithAtHash = idClaims.copy(at_hash = Some(at_hash))
      idToken            <- createIdToken(idClaimsWithAtHash)
    } yield TokenPair(idToken = idToken, accessToken = accessToken)
  }

  def createAccessToken(content: AccessClaims, coreClaims: JwtClaim = JwtClaim()): Try[AccessToken] =
    for {
      extraClaimsJson <- Try(write(content))
      claims          = claimWithDefaults(coreClaims.withContent(extraClaimsJson))
      header          = defaultHeader
      rawToken        <- encode(header, claims)
    } yield AccessToken(header, claims, content, rawToken)

  def createIdToken(content: IdClaims, coreClaims: JwtClaim = JwtClaim()): Try[IdToken] =
    for {
      extraClaimsJson <- Try(write(content))
      claims          = claimWithDefaults(coreClaims.withContent(extraClaimsJson))
      header          = defaultHeader
      rawToken        <- encode(header, claims)
    } yield IdToken(header, claims, content, rawToken)

  val hasher = Try(MessageDigest.getInstance("SHA-256"))

  def createFieldHash(data: String, algorithm: JwtAlgorithm): Try[String] =
    hasher.flatMap { hasher =>
      Try {
        val encodedToken      = JwtBase64.encodeString(data)
        val hash: Array[Byte] = hasher.digest(JwtBase64.encode(encodedToken))
        val halfHash          = hash.take(hash.size / 2)
        JwtBase64.encodeString(halfHash)
      }
    }

  implicit val clock = Clock.systemUTC()

  def claimWithDefaults(claim: JwtClaim = JwtClaim()): JwtClaim =
    claim
      .by(config.issuer)
      .to(config.audience)
      .issuedNow
      .expiresIn(10 * 60)
}

case class AccessClaims(sessionId: String)
case class IdClaims(
  id: String,
  name: String,
  email: String,
  at_hash: Option[String] = None
)

object IdClaims {
  def from(userInfo: UserInfo): IdClaims =
    IdClaims(
      id = userInfo.id.toString,
      name = userInfo.name,
      email = userInfo.email
    )
}

sealed trait Token[Content] {
  def header: JwtHeader

  /** Specific Token claims, supplementing the `coreClaims`; they should not overlap */
  def claims: JwtClaim

  /** Core claims defined in [[https://tools.ietf.org/html/rfc7519#section-4.1]] */
  def content: Content

  /** The raw, encoded token value, as received from the HTTP request of to be delivered in an HTTP response */
  def rawToken: String
}

case class IdToken(
  header: JwtHeader,
  claims: JwtClaim,
  content: IdClaims,
  rawToken: String
) extends Token[IdClaims]

case class AccessToken(
  header: JwtHeader,
  claims: JwtClaim,
  content: AccessClaims,
  rawToken: String
) extends Token[AccessClaims]

case class TokenPair(idToken: IdToken, accessToken: AccessToken)
