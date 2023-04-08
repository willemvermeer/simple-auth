import com.typesafe.sbt.packager.docker._

name := "akka-http-simple-auth"

version := "1.0"

scalaVersion := "2.13.1"

ThisBuild / useCoursier := false

val jreDockerBaseImage = "azul/zulu-openjdk-alpine:17.0.3"

lazy val basicSettings = Seq(
  organization := "com.example",
  name         := "akka-http-simple-auth",
  javacOptions ++= Seq("-source", "17", "-target", "17", "-Xlint"),
  scalaVersion := "2.13.1",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Ywarn-unused:imports",
    "-Xfatal-warnings"
  )
)
def assemblySettings(enabled: Boolean) = Seq(
  packageBin / assembleArtifact                := enabled,
  assemblyPackageScala / assembleArtifact      := enabled,
  assemblyPackageDependency / assembleArtifact := enabled
)

lazy val akkaVersion       = "2.7.0"
lazy val akkaHttpVersion   = "10.4.0"
lazy val postgresVersion   = "42.5.4"
lazy val hikariVersion     = "5.0.1"
lazy val logbackVersion    = "1.2.3"
lazy val akkaJson4sVersion = "1.39.2"
lazy val json4sVersion     = "4.0.6"
lazy val commCodecVersion  = "1.15"
lazy val jwtVersion        = "9.2.0"
lazy val bCastleVersion    = "1.70"

lazy val akkaLibraryDependencies: Seq[ModuleID] = Seq(
  "com.typesafe.akka"    %% "akka-stream"              % akkaVersion,
  "com.typesafe.akka"    %% "akka-http"                % akkaHttpVersion,
  "commons-codec"         % "commons-codec"            % commCodecVersion,
  "ch.qos.logback"        % "logback-classic"          % logbackVersion,
  "org.postgresql"        % "postgresql"               % postgresVersion,
  "com.zaxxer"            % "HikariCP"                 % hikariVersion,
  "de.heikoseeberger"    %% "akka-http-json4s"         % akkaJson4sVersion,
  "org.json4s"           %% "json4s-native"            % json4sVersion,
  "org.json4s"           %% "json4s-ext"               % json4sVersion,
  "com.github.jwt-scala" %% "jwt-core"                 % jwtVersion,
  "com.github.jwt-scala" %% "jwt-json4s-native"        % jwtVersion,
  "org.bouncycastle"      % "bcpkix-jdk15on"           % bCastleVersion,
  "com.typesafe.akka"    %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest"        %% "scalatest"                % "3.1.0"     % Test
)

lazy val server = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(assemblySettings(enabled = false): _*)
  .settings(
    name := "akka-http-simple-auth",
    libraryDependencies ++= akkaLibraryDependencies,
    Docker / packageName := "example.com/akka-http-simple-auth",
    dockerBaseImage      := jreDockerBaseImage,
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      ExecCmd("RUN", "apk", "add", "--no-cache", "bash"),
      ExecCmd("RUN", "addgroup", "-g", "1000", "-S", "incontaineruser"),
      ExecCmd(
        "RUN",
        "adduser",
        "-u",
        "1000",
        "-S",
        "incontaineruser",
        "-G",
        "incontaineruser"
      ),
      Cmd("WORKDIR", "/opt/docker"),
      Cmd("RUN", "chown", "incontaineruser:incontaineruser", "-R", "/opt"),
      Cmd("USER", "incontaineruser")
    ),
    Test / publishArtifact := true
  )
