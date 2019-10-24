name := "smile-shell"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true

autoScalaLibrary := true

mainClass in Compile := Some("smile.shell.Main")

// native packager
enablePlugins(JavaAppPackaging)

maintainer := "Haifeng Li <haifeng.hli@gmail.com>"

packageName := "smile"

packageSummary := "Smile"

packageDescription := "Statistical Machine Intelligence and Learning Engine"

executableScriptName := "smile"

bashScriptConfigLocation := Some("${app_home}/../conf/smile.ini")

bashScriptExtraDefines += """addJava "-Dsmile.home=${app_home}/..""""

bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/smile.conf""""

// native packager Docker plugin
enablePlugins(DockerPlugin)

import com.typesafe.sbt.packager.docker._

dockerBaseImage := "frolvlad/alpine-oraclejdk8"

packageName in Docker := "haifengl/smile"

dockerUpdateLatest := true

dockerCommands := dockerCommands.value.flatMap{
  case cmd@Cmd("FROM",_) => List(cmd, Cmd("RUN", "apk update && apk add bash"))
  case other => List(other)
}

// BuildInfo
enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "smile.shell"

buildInfoOptions += BuildInfoOption.BuildTime

libraryDependencies += "com.lihaoyi" % "ammonite" % "1.7.4" cross CrossVersion.full

libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.26"
