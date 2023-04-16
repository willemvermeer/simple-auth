import com.typesafe.sbt.packager.docker._
import sbt.Keys.libraryDependencies

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / useCoursier := false

val jreDockerBaseImage = "azul/zulu-openjdk-alpine:17.0.3"

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    name := "zio-simple-auth",
    buildInfoPackage := "com.example",
    Docker / packageName := "zio-simple-auth",
    dockerBaseImage := jreDockerBaseImage,
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
    libraryDependencies ++= Seq(
      "dev.zio"              %% "zio-http"          % "0.0.5",
      "dev.zio"              %% "zio-json"          % "0.5.0",
      "org.postgresql"       % "postgresql"         % "42.5.4",
      "com.zaxxer"           % "HikariCP"           % "5.0.1",
      "commons-codec"        % "commons-codec"      % "1.15",
      "com.github.jwt-scala" %% "jwt-core"          % "9.2.0",
      "com.typesafe"         % "config"             % "1.4.2",
      "org.bouncycastle"      % "bcpkix-jdk15on"    % "1.70",
      "dev.zio"              %% "zio-test"          % "2.0.5" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
