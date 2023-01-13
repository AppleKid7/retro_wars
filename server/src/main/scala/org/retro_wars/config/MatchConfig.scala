package org.retro_wars.config

import zio._
import zio.config._
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

case class MatchConfig(maxNumberOfMembers: Int)

object MatchConfig {
  val live: ZLayer[Any, ReadError[String], MatchConfig] =
    ZLayer {
      read {
        descriptor[MatchConfig].from(
          TypesafeConfigSource.fromResourcePath
            .at(PropertyTreePath.$("MatchConfig"))
        )
      }
    }
}
