package com.example

import com.example.auth.TokenCreator
import com.example.db.ConnectionPool
import com.example.route.TokenRoute
import org.bouncycastle.jce.provider.BouncyCastleProvider
import zio._
import zio.http._

object Main extends ZIOAppDefault {

  val run = ZIOAppArgs.getArgs.flatMap { args =>
    val simpleAuthConfig  = SimpleAuthConfig.load()
    val config            = ServerConfig.default.port(simpleAuthConfig.http.port)
    val configLayer       = ServerConfig.live(config)
    val simpleConfigLayer = ZLayer.succeed(SimpleAuthConfig.load())
    val pool              = ConnectionPool.live
    val tokenCreator      = TokenCreator.live

    // make sure we have a crypto implementation
    java.security.Security.addProvider(new BouncyCastleProvider())

    (Server.install(TokenRoute.app).flatMap { port =>
      Console.printLine(s"ZIO simple auth open for e-Business on port: $port")
    } *> ZIO.never)
      .provide(configLayer, Server.live, simpleConfigLayer, tokenCreator, pool)
  }
}
