package org.retro_wars

import com.devsisters.shardcake.{ EntityType, Replier, Sharding }
import dev.profunktor.redis4cats.RedisCommands
import zio.{ Dequeue, RIO, Task, ZIO }
import zio.config._
import scala.util.{ Failure, Success, Try }
import org.retro_wars.config.MatchConfig

object MatchBehavior {
  enum MatchMakingError {
    case MatchFull(message: String, maxCapacity: Int)
    def message: String
  }

  enum MatchMessage {
    case Join(userId: String, replier: Replier[Either[MatchMakingError, Set[String]]])
    case Leave(userId: String)
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
        case MatchMessage.Leave(userId)         =>
          redis.lRem(entityId, 1, userId).unit
      }
    }
}
