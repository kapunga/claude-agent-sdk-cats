val Scala3Version  = "3.3.4"
val CatsEffectVersion = "3.5.7"
val Fs2Version     = "3.11.0"
val CirceVersion   = "0.14.10"
val MunitCEVersion = "2.0.0"

lazy val commonSettings = Seq(
  scalaVersion := Scala3Version,
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Wunused:all",
  ),
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "claude-agent-sdk-cats",
    organization := "io.github.kapunga",
    version := "0.1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect"        % CatsEffectVersion,
      "co.fs2"        %% "fs2-core"           % Fs2Version,
      "co.fs2"        %% "fs2-io"             % Fs2Version,
      "io.circe"      %% "circe-core"         % CirceVersion,
      "io.circe"      %% "circe-parser"       % CirceVersion,
      "org.typelevel" %% "munit-cats-effect"  % MunitCEVersion % Test,
      "io.circe"      %% "circe-literal"      % CirceVersion   % Test,
    ),
  )

lazy val examples = (project in file("examples"))
  .dependsOn(root)
  .settings(commonSettings)
  .settings(
    name := "claude-agent-sdk-cats-examples",
    publish / skip := true,
  )
