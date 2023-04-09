package com.example.route

import com.example.SimpleAuthConfig
import zio.ZIO
import zio.json._
import zio.http.model.Method
import zio.http._

import java.nio.charset.StandardCharsets

case class TokenRequest(username: String, password: String)
object TokenRequest {
  implicit val decoder = DeriveJsonDecoder.gen[TokenRequest]
}

case class TokenResponse(access_token: String, id_token: String)
object TokenResponse {
  implicit val encoder = DeriveJsonEncoder.gen[TokenResponse]
}

object TokenRoute {

  val app: Http[SimpleAuthConfig, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case req @ Method.GET -> !! / "token" =>
        for {
          resp <- ZIO.succeed(Response.text("Try POSTing"))
        } yield resp
      case req @ Method.POST -> !! / "token" =>
        (for {
          config <- ZIO.service[SimpleAuthConfig]
          tReq <- req.body
                   .asString(StandardCharsets.UTF_8)
                   .map(_.fromJson[TokenRequest])
          resp <- tReq match {
                   case Left(err) => ZIO.succeed(Response.text(s"error $err"))
                   case Right(r) =>
                     println(s"Received $r")
                     val response = TokenResponse("access", "id")
                     ZIO.succeed(Response.json(response.toJson))
                 }
        } yield resp).catchAll(ex => ZIO.succeed(Response.text(s"error $ex")))
    }

}
