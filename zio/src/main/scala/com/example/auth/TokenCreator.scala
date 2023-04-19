package com.example.auth

import com.example.db.UserInfo
import com.example.{ AuthConfig, SimpleAuthConfig }
import pdi.jwt._
import zio.json._
import zio.{ ZIO, ZLayer }

import java.time.Clock
import scala.util.Try

case class IdClaims(
  id: String,
  name: String,
  email: String
)
object IdClaims {
  implicit val encoder = DeriveJsonEncoder.gen[IdClaims]
  def from(userInfo: UserInfo): IdClaims =
    IdClaims(
      id = userInfo.id.toString,
      name = userInfo.name,
      email = userInfo.email
    )
}

object TokenCreator {
  val live = ZLayer.fromZIO {
    for {
      config <- ZIO.service[SimpleAuthConfig]
    } yield TokenCreator(config.auth)
  }
}
case class TokenCreator(config: AuthConfig) {

  val algorithm     = JwtAlgorithm.fromString("RS256")
  val defaultHeader = JwtHeader(algorithm = Some(algorithm))

  def createToken(userInfo: UserInfo): Try[IdToken] =
    createIdToken(IdClaims.from(userInfo))

  private def createIdToken(content: IdClaims, coreClaims: JwtClaim = JwtClaim()): Try[IdToken] =
    for {
      extraClaimsJson <- Try(content.toJson)
      claims          = claimWithDefaults(coreClaims.withContent(extraClaimsJson))
      header          = defaultHeader
      rawToken        <- Try(Jwt.encode(header, claims, config.privateKey))
    } yield IdToken(header, claims, content, rawToken)

  implicit val clock = Clock.systemUTC()

  private def claimWithDefaults(claim: JwtClaim = JwtClaim()): JwtClaim =
    claim
      .by(config.issuer)
      .to(config.audience)
      .issuedNow
      .expiresIn(10 * 60)
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
