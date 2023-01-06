import NativePackagerHelper._

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.2.1"
ThisBuild / organization := "org.jponte"
ThisBuild / scalacOptions ++= Seq(
  "-Xmax-inlines",
  "64"
)

val circeVersion = "0.14.3"
val monocleVersion = "3.1.0"
val http4sVersion = "1.0.0-M36"
val logbackVersion = "1.4.5"
val zioVersion = "2.0.5"
val zioHttpVersion = "0.0.3"
val shardCakeVersion = "2.0.5"
val zioJsonVersion = "0.4.2"
val quillVersion = "4.6.0"

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "retro_wars_core",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "dev.optics" %% "monocle-core" % monocleVersion,
      "dev.optics" %% "monocle-macro" % monocleVersion,
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-test" % zioVersion,
      "dev.zio" %% "zio-test-sbt" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-test-junit" % zioVersion,
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "dev.zio" %% "zio-json" % zioJsonVersion,
      "io.getquill" %% "quill-jdbc-zio" % quillVersion,
      "org.postgresql" % "postgresql" % "42.3.1",
      "com.devsisters" % "shardcake-core_3" % shardCakeVersion,
      "com.devsisters" %% "shardcake-manager" % shardCakeVersion,
      "com.devsisters" %% "shardcake-storage-redis" % shardCakeVersion,
      "com.devsisters" %% "shardcake-protocol-grpc" % shardCakeVersion,
      "com.devsisters" %% "shardcake-serialization-kryo" % shardCakeVersion
    )
  )

lazy val game =
  (project in file("game"))
    .enablePlugins(ScalaJSPlugin, SbtIndigo)
    .settings(
      name := "retro_wars_game",
      showCursor := true,
      title := "Retro Wars",
      gameAssetsDirectory := "assets",
      windowStartWidth := 17 * 16 * 3,
      windowStartHeight := 12 * 16 * 3,
      libraryDependencies ++= Seq(
        "io.indigoengine" %%% "indigo" % "0.14.0",
        "io.indigoengine" %%% "indigo-extras" % "0.14.0",
        "io.indigoengine" %%% "indigo-json-circe" % "0.14.0",
        "dev.optics" %%% "monocle-core" % monocleVersion,
        "dev.optics" %%% "monocle-macro" % monocleVersion
      )
    )
    .dependsOn(core.js)

addCommandAlias("buildGame", ";compile;game/fastOptJS;game/indigoBuild")
addCommandAlias("runGame", ";compile;game/fastOptJS;game/indigoRun")
addCommandAlias("buildGameFull", ";compile;game/fullOptJS;game/indigoBuildFull")
addCommandAlias("runGameFull", ";compile;game/fullOptJS;game/indigoRunFull")

lazy val server = (project in file("server"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "retro_wars_game",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion % Runtime
    ),
    Universal / mappings ++= directory(
      baseDirectory.value / ".." / "game" / "target" / "indigoBuildFull"
    )
  )
  .dependsOn(core.jvm)
