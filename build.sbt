name := "smile"

import com.typesafe.sbt.pgp.PgpKeys.{useGpg, publishSigned, publishLocalSigned}

lazy val commonSettings = Seq(
  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("http://haifengl.github.io/")),
  version := "1.2.0",
  javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-g:lines,vars,source", "-Xlint:unchecked"),
  javacOptions in (Compile, doc) ++= Seq("-Xdoclint:none"),
  libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.21" % "test",
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8"),
  parallelExecution in Test := false,
  crossPaths := false,
  autoScalaLibrary := false,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false ,
  publishMavenStyle := true,
  useGpg := true,
  pomIncludeRepository := { _ => false },
  pomExtra := (
    <url>https://github.com/haifengl/smile</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:haifengl/smile.git</url>
        <connection>scm:git:git@github.com:haifengl/smile.git</connection>
      </scm>
      <developers>
        <developer>
          <id>haifengl</id>
          <name>Haifeng Li</name>
          <url>http://haifengl.github.io/</url>
        </developer>
      </developers>
  )
)

lazy val nonPubishSettings = commonSettings ++ Seq(
  //publishArtifact := false,
  publishLocal := {},
  publish := {},
  publishSigned := {},
  publishLocalSigned := {}
)

lazy val root = project.in(file(".")).settings(nonPubishSettings: _*)
  .aggregate(core, data, math, graph, plot, interpolation, nlp, demo, benchmark, scala, shell)

lazy val math = project.in(file("math")).settings(commonSettings: _*)

lazy val core = project.in(file("core")).settings(commonSettings: _*).dependsOn(data, math, graph)

lazy val data = project.in(file("data")).settings(commonSettings: _*).dependsOn(math)

lazy val graph = project.in(file("graph")).settings(commonSettings: _*).dependsOn(math)

lazy val interpolation = project.in(file("interpolation")).settings(commonSettings: _*).dependsOn(math)

lazy val nlp = project.in(file("nlp")).settings(commonSettings: _*).dependsOn(core)

lazy val plot = project.in(file("plot")).settings(commonSettings: _*).dependsOn(core)

lazy val demo = project.in(file("demo")).settings(nonPubishSettings: _*).dependsOn(core, interpolation, plot)

lazy val benchmark = project.in(file("benchmark")).settings(nonPubishSettings: _*).dependsOn(core, scala)

lazy val scala = project.in(file("scala")).settings(commonSettings: _*).dependsOn(interpolation, nlp, plot)

lazy val shell = project.in(file("shell")).settings(nonPubishSettings: _*).dependsOn(benchmark, demo, scala)
