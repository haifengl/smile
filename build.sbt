name := "smile"

import com.typesafe.sbt.pgp.PgpKeys.{useGpg, publishSigned, publishLocalSigned}

lazy val commonSettings = Seq(
  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("http://haifengl.github.io/")),
  version := "1.5.1",
  javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF8", "-g:lines,vars,source", "-Xlint:unchecked"),
  javacOptions in (Compile, doc) ++= Seq("-Xdoclint:none"),
  javaOptions in test += "-Dsmile.threads=1",
  libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.25" % "test",
  libraryDependencies += "junit" % "junit" % "4.12" % "test",
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
  scalaVersion := "2.12.4",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8"),
  testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a")),
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
  .aggregate(core, data, math, nd4j, netlib, graph, plot, interpolation, nlp, demo, benchmark, scala, shell)

lazy val math = project.in(file("math")).settings(commonSettings: _*)

lazy val nd4j = project.in(file("nd4j")).settings(commonSettings: _*).dependsOn(math)

lazy val netlib = project.in(file("netlib")).settings(commonSettings: _*).dependsOn(math)

lazy val symbolic = project.in(file("symbolic")).settings(commonSettings: _*)

lazy val core = project.in(file("core")).settings(commonSettings: _*).dependsOn(data, math, graph, netlib % "test")

lazy val data = project.in(file("data")).settings(commonSettings: _*).dependsOn(math)

lazy val graph = project.in(file("graph")).settings(commonSettings: _*).dependsOn(math)

lazy val interpolation = project.in(file("interpolation")).settings(commonSettings: _*).dependsOn(math)

lazy val nlp = project.in(file("nlp")).settings(commonSettings: _*).dependsOn(core)

lazy val plot = project.in(file("plot")).settings(commonSettings: _*).dependsOn(core)

lazy val demo = project.in(file("demo")).settings(nonPubishSettings: _*).dependsOn(core, interpolation, plot)

lazy val benchmark = project.in(file("benchmark")).settings(nonPubishSettings: _*).dependsOn(core, scala)

lazy val scala = project.in(file("scala")).settings(commonSettings: _*).dependsOn(core, interpolation, nlp, plot)

lazy val shell = project.in(file("shell")).settings(nonPubishSettings: _*).dependsOn(benchmark, demo, scala, netlib)
