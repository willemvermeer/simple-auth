package com.example.auth

import com.example.AuthConfig
import com.example.db.UserInfo
import org.json4s.ext.JavaTypesSerializers
import org.json4s.{ DefaultFormats, Formats }
import pdi.jwt._

import java.time.Clock
import scala.util.Try

case class TokenCreator(config: AuthConfig) {

  import org.json4s.native.Serialization._
  implicit val formats: Formats = DefaultFormats ++ JavaTypesSerializers.all

  val algorithm     = JwtAlgorithm.fromString("RS256")
  val defaultHeader = JwtHeader(algorithm = Some(algorithm))

  def createToken(userInfo: UserInfo): Try[IdToken] =
    createIdToken(IdClaims.from(userInfo))

  private def createIdToken(content: IdClaims, coreClaims: JwtClaim = JwtClaim()): Try[IdToken] =
    for {
      extraClaimsJson <- Try(write(content))
      claims          = claimWithDefaults(coreClaims.withContent(extraClaimsJson))
      header          = defaultHeader
      rawToken        <- Try(Jwt.encode(header, claims, config.privateKey))
    } yield IdToken(header, claims, content, rawToken)

  implicit val clock = Clock.systemUTC()

  def claimWithDefaults(claim: JwtClaim = JwtClaim()): JwtClaim =
    claim
      .by(config.issuer)
      .to(config.audience)
      .issuedNow
      .expiresIn(10 * 60)
}

case class IdClaims(
  id: String,
  name: String,
  email: String
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
