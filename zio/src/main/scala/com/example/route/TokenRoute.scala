package com.example.route

import com.example.SimpleAuthConfig
import com.example.auth.TokenCreator
import com.example.db.{ DbQuery, HikariConnectionPool }
import zio.{ Console, Duration, UIO, ZIO }
import zio.http._
import zio.http.model.Method
import zio.json._

import java.nio.charset.StandardCharsets
import java.time.temporal.ChronoUnit.NANOS
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
          tReq <- req.body
                   .asString(StandardCharsets.UTF_8)
                   .map(_.fromJson[TokenRequest])
          pool <- ZIO.service[HikariConnectionPool]
          tuple <- tReq match {
                    case Left(err) => ZIO.fail(err).timed
                    case Right(r) =>
                      ZIO.fromTry(DbQuery.userInfo(r.username, pool)).timed
                  }
          userInfo     = tuple._2
          tokenCreator <- ZIO.service[TokenCreator]
          tuple2       <- ZIO.fromTry(tokenCreator.createTokenPair(userInfo, UUID.randomUUID().toString)).timed
          pair         = tuple2._2
          response     = TokenResponse(pair.accessToken.rawToken, pair.idToken.rawToken)
          _            <- Console.printLine(s"ZIO Db time ${toMs(tuple._1)}ms token creation ${toMs(tuple2._1)}ms.")
          resp         <- ZIO.succeed(Response.json(response.toJson))
        } yield resp).catchAll(ex => ZIO.succeed(Response.text(s"error $ex")))
    }

  private def toMs(dur: Duration) = s"${dur.get(NANOS) / 1e6}ms"
}
