package org.retro_wars

import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces._
import org.retro_wars.config._
import zio._

object ShardManagerApp extends ZIOAppDefault {
  def run: Task[Nothing] =
    Server
      .run
      .provide(
        ZLayer.succeed(ManagerConfig.default),
        ZLayer.succeed(GrpcConfig.default),
        ZLayer.succeed(RedisConfig.default),
        RedisUriConfig.live,
        redis,
        StorageRedis.live,
        PodsHealth.local, // just ping a pod to see if it's alive
        GrpcPods.live, // use gRPC protocol
        ShardManager.live // Shard Manager logic
      )
}
