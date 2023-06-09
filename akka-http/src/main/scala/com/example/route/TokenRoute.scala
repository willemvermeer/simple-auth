package com.example.route

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import com.example.auth.{ KeyTools, TokenCreator }
import com.example.db.{ DbQuery, HikariConnectionPool }
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.native.Serialization
import org.json4s.{ native, DefaultFormats }

import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

case class TokenRequest(username: String, password: String)
case class TokenResponse(access_token: String, id_token: String)

case class TokenRoute(dbPool: HikariConnectionPool, tokenCreator: TokenCreator)(implicit val system: ActorSystem) {
  implicit val serialization: Serialization.type = native.Serialization
  implicit val formats: DefaultFormats.type      = DefaultFormats

  private implicit def ec: ExecutionContext = system.dispatcher

  def route = path("token") {
    post {
      entity(as[TokenRequest]) { tokenRequest =>
        val now = System.currentTimeMillis()
        onComplete(for {
          userInfo <- Future.fromTry(DbQuery.userInfo(tokenRequest.username, dbPool))
          timeDb   = System.currentTimeMillis()
          passwordOk <- Future.fromTry(
                         KeyTools
                           .verifyHashMatch(tokenRequest.password, userInfo.salt, userInfo.hashpassword)
                       )
          timeHash  = System.currentTimeMillis()
          _         <- if (passwordOk) Future.successful(()) else Future.failed(new RuntimeException("Incorrect password"))
          tokenPair <- Future.fromTry(tokenCreator.createTokenPair(userInfo, UUID.randomUUID().toString))
          timeToken = System.currentTimeMillis()
          _ = println(
            s"Akka Db time ${timeDb - now}ms Password hash ${timeHash - timeDb}ms token creation ${timeToken - timeHash}ms."
          )
          response <- Future {
                       TokenResponse(
                         access_token = tokenPair.accessToken.rawToken,
                         id_token = tokenPair.idToken.rawToken
                       )
                     }
        } yield response) {
          case Success(value) =>
            complete(StatusCodes.OK, value)
          case Failure(failure) =>
            failure.printStackTrace()
            complete(StatusCodes.InternalServerError, failure.getMessage)
        }
      }
    } ~ get {
      complete(StatusCodes.OK, "Server is up, Try POSTing")
    }
  }

}
