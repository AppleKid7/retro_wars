package org.retro_wars

import com.devsisters.shardcake.{EntityType, Replier, Sharding}
import dev.profunktor.redis4cats.RedisCommands
import org.retro_wars.config.MatchConfig
import scala.util.{Failure, Success, Try}
import zio.{Dequeue, RIO, Task, ZIO}
import zio.config.*
import sttp.tapir.Schema
import zio.json.*
import zio.json.{DeriveJsonCodec, JsonCodec}

object MatchBehavior {
  enum MatchMakingError {
    case MatchFull(message: String, maxCapacity: Int)
    // object MatchFull {
    //   given zio.json.JsonEncoder[MatchFull] =
    //     DeriveJsonEncoder.gen[MatchFull]

    //   given zio.json.JsonDecoder[MatchFull] =
    //     DeriveJsonDecoder.gen[MatchFull]
    // }
    case InvalidUserId(message: String)
    // object InvalidUserId {
    //   given zio.json.JsonEncoder[InvalidUserId] =
    //     DeriveJsonEncoder.gen[InvalidUserId]

    //   given zio.json.JsonDecoder[InvalidUserId] =
    //     DeriveJsonDecoder.gen[InvalidUserId]
    // }
    case InvalidJson(message: String)
    // object InvalidJson {
    //   given zio.json.JsonEncoder[InvalidJson] =
    //     DeriveJsonEncoder.gen[InvalidJson]

    //   given zio.json.JsonDecoder[InvalidJson] =
    //     DeriveJsonDecoder.gen[InvalidJson]
    // }
    case NetworkReadError(message: String)
    // object NetworkReadError {
    //   given zio.json.JsonEncoder[NetworkReadError] =
    //     DeriveJsonEncoder.gen[NetworkReadError]

    //   given zio.json.JsonDecoder[NetworkReadError] =
    //     DeriveJsonDecoder.gen[NetworkReadError]
    // }
    case ShardcakeConnectionError(message: String)
    // object ShardcakeConnectionError {
    //   given zio.json.JsonEncoder[ShardcakeConnectionError] =
    //     DeriveJsonEncoder.gen[ShardcakeConnectionError]

    //   given zio.json.JsonDecoder[ShardcakeConnectionError] =
    //     DeriveJsonDecoder.gen[ShardcakeConnectionError]
    // }
    def message: String
  }
  object MatchMakingError {
    given zio.json.JsonEncoder[MatchMakingError] =
      DeriveJsonEncoder.gen[MatchMakingError]

    given zio.json.JsonDecoder[MatchMakingError] =
      DeriveJsonDecoder.gen[MatchMakingError]
  }

  enum MatchMessage {
    case Join(userId: String, replier: Replier[Either[MatchMakingError, Set[String]]])
    case Leave(userId: String, replier: Replier[Either[MatchMakingError, String]])
  }

  case class MatchResponse(status: Int, message: Option[String])

  object Match extends EntityType[MatchMessage]("match")

  def behavior(
      entityId: String,
      messages: Dequeue[MatchMessage]
  ): RIO[Sharding with RedisCommands[Task, String, String] with MatchConfig, Nothing] =
    ZIO.serviceWithZIO[RedisCommands[Task, String, String]](redis =>
      ZIO.logInfo(s"Started entity $entityId") *>
        messages.take.flatMap(handleMessage(entityId, redis, _)).forever
    )

  def handleMessage(
      entityId: String,
      redis: RedisCommands[Task, String, String],
      message: MatchMessage
  ): RIO[Sharding with MatchConfig, Unit] =
    getConfig[MatchConfig].flatMap { config =>
      message match {
        case MatchMessage.Join(userId, replier) =>
          redis
            .lRange(entityId, 0, -1)
            .map(_.toSet)
            .flatMap(members =>
              if (members.size >= config.maxNumberOfMembers)
                replier.reply(
                  Left(
                    MatchMakingError.MatchFull(
                      "You can no longer join this Match!",
                      config.maxNumberOfMembers
                    )
                  )
                )
              else
                (redis.lPush(entityId, userId) *>
                  replier.reply(Right(members + userId))).unless(members.contains(userId)).unit
            )
        case MatchMessage.Leave(userId, replier) =>
          redis
            .lRange(entityId, 0, -1)
            .map(_.toSet)
            .flatMap(members =>
              if (!members.contains(userId))
                replier.reply(
                  Left(
                    MatchMakingError.InvalidUserId(
                      s"Invalid User ID $userId"
                    )
                  )
                )
              else
                (redis.lRem(entityId, 1, userId) *>
                  replier.reply(Right(s"$userId has left the match!")))
                  .unless(!members.contains(userId))
                  .unit
            )
      }
    }
}
