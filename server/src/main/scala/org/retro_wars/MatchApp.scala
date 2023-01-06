package org.retro_wars

import com.devsisters.shardcake
import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces._
import dev.profunktor.redis4cats.RedisCommands
import io.getquill._
import io.getquill.jdbczio.Quill
import java.util.UUID.randomUUID
import java.sql.SQLException
import scala.util.{Try, Success, Failure}
import zhttp.http._
import zhttp.service.Server
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._
import zio.schema.Schema._
import zio.schema._
import zhttp.service.ChannelFactory
import org.retro_wars.MatchBehavior._
import org.retro_wars.MatchBehavior.MatchMessage.Join
import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces._
import dev.profunktor.redis4cats.RedisCommands


object MatchApp extends ZIOAppDefault {
  val config: ZLayer[Any, SecurityException, shardcake.Config] =
    ZLayer(
      System
        .env("port")
        .map(
          _.flatMap(_.toIntOption).fold(shardcake.Config.default)(port =>
            shardcake.Config.default.copy(shardingPort = port)
          )
        )
    )

  private val PORT = 8090 // TODO - Move to Config DB or environment variable

  val app: Http[Scope & Sharding, Throwable, Request, Response] = Http.collectZIO[Request] {
    case Method.GET -> !! / "text" =>
      ZIO.unit.map(_ => Response.text("Hello World!"))
    case Method.POST -> !! / "matches" =>
      val result: ZIO[Sharding, Throwable, Either[MatchMakingError, Set[String]]] = for {
        matchShard <- Sharding.messenger(MatchBehavior.Match)
        res   <- matchShard.send[Either[MatchMakingError, Set[String]]](s"match1")(Join(s"user-${randomUUID()}", _))
      } yield res
      result.map(res => {
        res match {
          case Right(value) =>
            Response.text(s"success: $value").setStatus(Status.Ok)
          case Left(ex) =>
            Response.text(s"failure: ${ex.message}").setStatus(Status.BadRequest)
        }
      })
  }

  private val server =
    Server.port(PORT) ++              // Setup port
      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(app)                 // Setup the Http app

  private val register = for {
    _ <- Sharding.registerEntity(
      MatchBehavior.Match,
      MatchBehavior.behavior
    )
    _ <- Sharding.registerScoped
  } yield ()

  val run: ZIO[Environment & (ZIOAppArgs & Scope), Any, Any] = ZIOAppArgs.getArgs.flatMap { args =>
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

    // Create a new server
    (register *> server.make
      .flatMap(start =>
        // Waiting for the server to start then make sure it stays up forever with ZIO.never
        Console.printLine(s"Server started on port ${start.port}") *> ZIO.never,
      ))
      .provide(
        config,
        ServerChannelFactory.auto,
        // ChannelFactory.auto,
        EventLoopGroup.auto(nThreads),
        Scope.default,
        ZLayer.succeed(GrpcConfig.default),
        ZLayer.succeed(RedisConfig.default),
        redis,
        KryoSerialization.live,
        StorageRedis.live,
        ShardManagerClient.liveWithSttp,
        GrpcPods.live,
        Sharding.live,
        GrpcShardingService.live
      )
  }
}
