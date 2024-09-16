package org.retro_wars.config

import zio._
import zio.config._
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

case class RedisUriConfig(uri: String, port: Int)

object RedisUriConfig {
  val live: ZLayer[Any, ReadError[String], RedisUriConfig] =
    ZLayer {
      read {
        descriptor[RedisUriConfig].from(
          TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("RedisUriConfig"))
        )
      }
    }
}
