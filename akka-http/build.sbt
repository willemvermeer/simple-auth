import com.typesafe.sbt.packager.docker._

ThisBuild / scalaVersion := "2.13.10"
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

lazy val akkaLibraryDependencies: Seq[ModuleID] = Seq(
  "com.typesafe.akka"    %% "akka-stream"              % "2.7.0",
  "com.typesafe.akka"    %% "akka-http"                % "10.4.0",
  "commons-codec"         % "commons-codec"            % "1.15",
  "ch.qos.logback"        % "logback-classic"          % "1.2.3",
  "org.postgresql"        % "postgresql"               % "42.5.4",
  "com.zaxxer"            % "HikariCP"                 % "5.0.1",
  "de.heikoseeberger"    %% "akka-http-json4s"         % "1.39.2",
  "org.json4s"           %% "json4s-native"            % "4.0.6",
  "org.json4s"           %% "json4s-ext"               % "4.0.6",
  "com.github.jwt-scala" %% "jwt-core"                 % "9.2.0",
  "com.github.jwt-scala" %% "jwt-json4s-native"        % "9.2.0",
  "org.bouncycastle"      % "bcpkix-jdk15on"           % "1.70",
  "com.typesafe.akka"    %% "akka-actor-testkit-typed" % "2.7.0" % Test,
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
