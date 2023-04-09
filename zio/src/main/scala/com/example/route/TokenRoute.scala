package com.example.route

import com.example.SimpleAuthConfig
import com.example.auth.TokenCreator
import com.example.db.{ DbQuery, HikariConnectionPool }
import zio.ZIO
import zio.json._
import zio.http.model.Method
import zio.http._

import java.nio.charset.StandardCharsets
import java.util.UUID

case class TokenRequest(username: String, password: String)
object TokenRequest {
  implicit val decoder = DeriveJsonDecoder.gen[TokenRequest]
}

case class TokenResponse(access_token: String, id_token: String)
object TokenResponse {
  implicit val encoder = DeriveJsonEncoder.gen[TokenResponse]
}

object TokenRoute {

  val app: Http[SimpleAuthConfig with HikariConnectionPool with TokenCreator, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "token" =>
        for {
          resp <- ZIO.succeed(Response.text("Try POSTing"))
        } yield resp
      case req @ Method.POST -> !! / "token" =>
        (for {
          config <- ZIO.service[SimpleAuthConfig]
          tReq <- req.body
                   .asString(StandardCharsets.UTF_8)
                   .map(_.fromJson[TokenRequest])
          pool <- ZIO.service[HikariConnectionPool]
          userInfo <- tReq match {
                       case Left(err) => ZIO.fail(err)
                       case Right(r) =>
                         println(s"Received $r")
                         ZIO.fromTry(DbQuery.userInfo(r.username, pool))
                     }
          tokenCreator <- ZIO.service[TokenCreator]
          pair         <- ZIO.fromTry(tokenCreator.createTokenPair(userInfo, UUID.randomUUID().toString))
          response     = TokenResponse(pair.accessToken.rawToken, pair.idToken.rawToken)
          resp         <- ZIO.succeed(Response.json(response.toJson))
        } yield resp).catchAll(ex => ZIO.succeed(Response.text(s"error $ex")))
    }

}
