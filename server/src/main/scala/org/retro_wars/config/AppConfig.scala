package org.retro_wars.config

import zio._
import zio.config._
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

case class AppConfig(port: Int)

object AppConfig {
  val live: ZLayer[Any, ReadError[String], AppConfig] =
    ZLayer {
      read {
        descriptor[AppConfig].from(
          TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("AppConfig"))
        )
      }
    }
}
