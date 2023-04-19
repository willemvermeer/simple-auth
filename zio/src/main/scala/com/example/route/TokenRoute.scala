package com.example.route

import com.example.SimpleAuthConfig
import com.example.auth.{ KeyTools, TokenCreator }
import com.example.db.{ DbQuery, HikariConnectionPool }
import zio.http._
import zio.http.model.Method
import zio.json._
import zio.{ Console, Duration, ZIO }

import java.nio.charset.StandardCharsets.UTF_8
import java.time.temporal.ChronoUnit.NANOS

case class TokenRequest(username: String, password: String)
object TokenRequest {
  implicit val decoder = DeriveJsonDecoder.gen[TokenRequest]
}

case class TokenResponse(id_token: String)
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
          tokenReq <- ZIO.absolve(req.body.asString(UTF_8).map(_.fromJson[TokenRequest]))
          pool     <- ZIO.service[HikariConnectionPool]
          timedDb  <- ZIO.fromTry(DbQuery.userInfo(tokenReq.username, pool)).timed
          userInfo = timedDb._2
          timedHash <- ZIO
                        .fromTry(
                          KeyTools
                            .verifyHashMatch(tokenReq.password, userInfo.salt, userInfo.hashpassword)
                        )
                        .timed
          _            <- ZIO.cond(timedHash._2, (), "Incorrect password")
          tokenCreator <- ZIO.service[TokenCreator]
          timedToken   <- ZIO.fromTry(tokenCreator.createToken(userInfo)).timed
          token        = timedToken._2
          response     = TokenResponse(token.rawToken)
          _ <- Console.printLine(
                s"ZIO Db time ${toMs(timedDb._1)} Password hash ${toMs(timedHash._1)} Token creation ${toMs(timedToken._1)}."
              )
          resp <- ZIO.succeed(Response.json(response.toJson))
        } yield resp).catchAll(ex => ZIO.succeed(Response.text(s"error $ex")))
    }

  private def toMs(dur: Duration) = s"${dur.get(NANOS) / 1e6}ms"
}
