package org

import com.devsisters.shardcake.StorageRedis.Redis
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.pubsub.PubSub
import zio.config._
import zio.interop.catz._
import zio.{ Task, ZEnvironment, ZIO, ZLayer }
import org.retro_wars.config.RedisUriConfig

package object retro_wars {
  val redis: ZLayer[RedisUriConfig, Throwable, Redis] =
    ZLayer.scopedEnvironment[RedisUriConfig] {
      implicit val runtime: zio.Runtime[Any] = zio.Runtime.default
      implicit val logger: Log[Task]         = new Log[Task] {
        override def debug(msg: => String): Task[Unit] = ZIO.logDebug(msg)
        override def error(msg: => String): Task[Unit] = ZIO.logError(msg)
        override def info(msg: => String): Task[Unit]  = ZIO.logInfo(msg)
      }

      getConfig[RedisUriConfig].flatMap { config =>
        (for {
          client   <- RedisClient[Task].from(s"${config.uri}:${config.port}")
          commands <- Redis[Task].fromClient(client, RedisCodec.Utf8)
          pubSub   <- PubSub.mkPubSubConnection[Task, String, String](client, RedisCodec.Utf8)
        } yield ZEnvironment(commands, pubSub)).toScopedZIO
      }
    }
}
