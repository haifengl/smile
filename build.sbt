name := "smile"

lazy val commonSettings = Seq(
  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("https://github.com/haifengl/smile")),
  version := "1.1.0-SNAPSHOT",
  javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-g:lines"),
  javacOptions in (Compile, doc) ++= Seq("-Xdoclint:none"),
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8"),
  parallelExecution in Test := false,
  crossPaths := false,
  autoScalaLibrary := false,
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  }
)

lazy val root = project.in(file(".")).settings(commonSettings: _*)
  .aggregate(core, data, math, graph, plot, interpolation, nlp, demo, benchmark, scala, shell)

// Don't publish to central Maven repo
publishArtifact := false

lazy val math = project.in(file("math")).settings(commonSettings: _*)

lazy val core = project.in(file("core")).settings(commonSettings: _*).dependsOn(data, math, graph)

lazy val data = project.in(file("data")).settings(commonSettings: _*).dependsOn(math)

lazy val graph = project.in(file("graph")).settings(commonSettings: _*).dependsOn(math)

lazy val interpolation = project.in(file("interpolation")).settings(commonSettings: _*).dependsOn(math)

lazy val nlp = project.in(file("nlp")).settings(commonSettings: _*).dependsOn(core)

lazy val plot = project.in(file("plot")).settings(commonSettings: _*).dependsOn(core)

lazy val demo = project.in(file("demo")).settings(commonSettings: _*).dependsOn(core, interpolation, plot)

lazy val benchmark = project.in(file("benchmark")).settings(commonSettings: _*).dependsOn(core, scala)

lazy val scala = project.in(file("scala")).settings(commonSettings: _*).dependsOn(interpolation, nlp, plot)

lazy val shell = project.in(file("shell")).settings(commonSettings: _*).dependsOn(benchmark, demo, scala)
