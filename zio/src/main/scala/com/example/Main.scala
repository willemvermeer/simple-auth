import com.example.SimpleAuthConfig
import com.example.auth.TokenCreator
import com.example.db.ConnectionPool
import com.example.route.TokenRoute
import zio._
import zio.http._

object Main extends ZIOAppDefault {
  // Set a port
  private val PORT = 8770

  val run = ZIOAppArgs.getArgs.flatMap { args =>
    val simpleAuthConfig  = SimpleAuthConfig.load()
    val config            = ServerConfig.default.port(simpleAuthConfig.http.port).maxThreads(100)
    val configLayer       = ServerConfig.live(config)
    val simpleConfigLayer = ZLayer.succeed(SimpleAuthConfig.load())
    val pool              = ConnectionPool.live
    val tokenCreator      = TokenCreator.live
//    val tokenFactoryHolder: ZLayer[SimpleAuthConfig, Nothing, TokenCreatorHolder] = TokenCreatorHolder.live

    (Server.install(TokenRoute.app).flatMap { port =>
      Console.printLine(s"Started server on port: $port")
    } *> ZIO.never)
      .provide(configLayer, Server.live, simpleConfigLayer, tokenCreator, pool)
  }
}
