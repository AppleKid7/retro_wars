package org.retro_wars

import com.devsisters.shardcake
import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces._
import dev.profunktor.redis4cats.RedisCommands
import io.getquill._
import io.getquill.jdbczio.Quill
import java.util.UUID.randomUUID
import org.retro_wars.MatchBehavior._
import org.retro_wars.MatchBehavior.MatchMessage.*
import org.retro_wars.config._
import scala.util.{Failure, Success, Try}
// import zhttp.http._
// import zhttp.service
// import zhttp.service.{EventLoopGroup, Server}
// import zhttp.service.ChannelFactory
// import zhttp.service.Server
// import zhttp.service.server.ServerChannelFactory
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.ziohttp.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.*
import sttp.tapir.ztapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.json.zio.*
import sttp.tapir.generic.auto.*
import sttp.client3.httpclient.zio._
import zio.*
import zio.config.*
import zio.json.*

object MatchApp extends ZIOAppDefault {
  val config: ZLayer[ShardcakeConfig, SecurityException, shardcake.Config] =
    ZLayer(getConfig[ShardcakeConfig].map { config =>
      shardcake.Config.default.copy(shardingPort = config.port)
    })

  // val config: ZLayer[Any, SecurityException, shardcake.Config] =
  //   ZLayer(
  //     System
  //       .env("port")
  //       .map(_.flatMap(_.toIntOption).fold(shardcake.Config.default)(port => shardcake.Config.default.copy(shardingPort = port)))
  //   )

  /*
  case Method.GET -> !! / "text" =>
      ZIO.unit.map(_ => Response.text("Hello World!"))
    case Method.POST -> !! / "join" =>
      (for {
        matchShard <- Sharding.messenger(MatchBehavior.Match)
        res <- matchShard
          .send[Either[MatchMakingError, Set[String]]](s"match1")(Join(s"user-${randomUUID()}", _))
          .orDie
        value <- ZIO.fromEither(res)
      } yield Response.json(UserJoinResponse(value.toList).toJson).setStatus(Status.Ok))
    case req @ (Method.POST -> !! / "leave") =>
   */

  val joinEndpoint: PublicEndpoint[Unit, MatchMakingError, UserJoinResponse, Any] =
    sttp
      .tapir
      .endpoint
      .in("join")
      .out(jsonBody[UserJoinResponse])
      .errorOut(jsonBody[MatchMakingError])

  val joinServerEndpoint: ZServerEndpoint[Sharding, Any] =
    joinEndpoint.zServerLogic(_ =>
      val test: ZIO[Sharding, MatchMakingError, UserJoinResponse] = for {
        matchShard <- Sharding.messenger(MatchBehavior.Match)
        res <- matchShard
          .send[Either[MatchMakingError, Set[String]]](s"match1")(Join(s"user-${randomUUID()}", _))
          .orDie
        value <- ZIO.fromEither(res)
      } yield UserJoinResponse(value.toList)
      test
    )

  // // Docs
  // val swaggerEndpoints: List[ServerEndpoint[Sharding, Task]] =
  //   SwaggerInterpreter().fromEndpoints[Task](List(joinEndpoint), "Join", "1.0")

  // val app: Http[Scope & Sharding, MatchMakingError, Request, Response] = Http.collectZIO[Request] {
  //   case Method.GET -> !! / "text" =>
  //     ZIO.unit.map(_ => Response.text("Hello World!"))
  //   case Method.POST -> !! / "join" =>
  //     (for {
  //       matchShard <- Sharding.messenger(MatchBehavior.Match)
  //       res <- matchShard
  //         .send[Either[MatchMakingError, Set[String]]](s"match1")(Join(s"user-${randomUUID()}", _))
  //         .orDie
  //       value <- ZIO.fromEither(res)
  //     } yield Response.json(UserJoinResponse(value.toList).toJson).setStatus(Status.Ok))
  //   case req @ (Method.POST -> !! / "leave") =>
  //     for {
  //       either <- req
  //         .bodyAsString
  //         .mapError(e => MatchMakingError.NetworkReadError(e.getMessage()))
  //         .map(_.fromJson[UserLeave])
  //       data <- ZIO.fromEither(either).mapError(e => MatchMakingError.InvalidJson(e))
  //       matchShard <- Sharding.messenger(MatchBehavior.Match)
  //       res <- matchShard
  //         .send[Either[MatchMakingError, String]](s"match1")(
  //           Leave(s"user-${data.id}", _)
  //         )
  //         .mapError(e => MatchMakingError.ShardcakeConnectionError(e.getMessage()))
  //       value <- ZIO.fromEither(res)
  //     } yield Response.json(s"""{"success": "$value"}""").setStatus(Status.Ok)
  // }

  // private val server =
  //   Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
  //     Server.app(
  //       app.catchAll(ex =>
  //         Http.succeed(
  //           Response.json(s"""{"failure": "${ex.message}"}""").setStatus(Status.BadRequest)
  //         )
  //       )
  //     ) // Setup the Http app

  private val register = for {
    _ <- Sharding.registerEntity(
      MatchBehavior.Match,
      MatchBehavior.behavior
    )
    _ <- Sharding.registerScoped
  } yield ()

  // val run: ZIO[Environment & (ZIOAppArgs & Scope), Any, Any] = ZIOAppArgs.getArgs.flatMap { args =>
  //   // Configure thread count using CLI
  //   val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

  //   // Create a new server
  //   getConfig[AppConfig]
  //     .flatMap { c =>
  //       (register *>
  //         server
  //           .withPort(c.port)
  //           .make
  //           .flatMap(start =>
  //             // Waiting for the server to start then make sure it stays up forever with ZIO.never
  //             Console.printLine(s"Server started on port ${start.port}") *> ZIO.never,
  //           )).provide(
  //         config,
  //         ServerChannelFactory.auto,
  //         EventLoopGroup.auto(nThreads),
  //         Scope.default,
  //         ZLayer.succeed(GrpcConfig.default),
  //         ZLayer.succeed(RedisConfig.default),
  //         RedisUriConfig.live,
  //         redis,
  //         KryoSerialization.live,
  //         StorageRedis.live,
  //         ShardManagerClient.liveWithSttp,
  //         GrpcPods.live,
  //         Sharding.live,
  //         GrpcShardingService.live,
  //         MatchConfig.live
  //       )
  //     }
  //     .provide(AppConfig.live)
  // }

  val myDecodeFailureHandler = DefaultDecodeFailureHandler
    .default
    .copy(
      respond = DefaultDecodeFailureHandler.respond(
        _,
        badRequestOnPathErrorIfPathShapeMatches = true,
        badRequestOnPathInvalidIfPathShapeMatches = true
      )
    )
  val joinEndpointOptions: ZioHttpServerOptions[Sharding] = ZioHttpServerOptions
    .customiseInterceptors[Sharding]
    .decodeFailureHandler(myDecodeFailureHandler)
    .options

  // val routes = ZioHttpInterpreter().toHttp(List(joinServerEndpoint) ++ swaggerEndpoints)
  val routes: http.Http[Sharding, Throwable, http.Request, http.Response] =
    ZioHttpInterpreter().toHttp(List(joinServerEndpoint))

  private val server: URIO[Sharding & http.Server, Nothing] =
    zio
      .http
      .Server
      .serve(
        routes.catchAll(ex =>
          zio
            .http
            .Http
            .succeed(
              zio.http.Response.json(s"""{"failure": "${ex}"}""")
            )
        )
      ) // Setup the Http app

  val run = getConfig[AppConfig]
    .flatMap { c =>
      (register *>
        zio
          .http
          .Server
          .serve(routes)
          .flatMap(_ => Console.printLine(s"Server started")) *> ZIO.never)
        .provide(
          zio.http.ServerConfig.live(zio.http.ServerConfig.default.port(c.port)),
          zio.http.Server.live,
          ZLayer.succeed(RedisConfig.default),
          RedisUriConfig.live,
          redis,
          StorageRedis.live,
          KryoSerialization.live,
          Sharding.live,
          GrpcShardingService.live,
          ZLayer.succeed(GrpcConfig.default),
          GrpcPods.live,
          config,
          sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend.layer(),
          ShardManagerClient.live,
          MatchConfig.live,
          ShardcakeConfig.live,
          Scope.default
        )
        .exitCode
    }
    .provide(AppConfig.live)
}
