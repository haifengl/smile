name := "smile"

lazy val commonSettings = Seq(
  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("http://haifengl.github.io/")),
  version := "2.2.2",
  javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF8", "-g:lines,vars,source", "-Xlint:unchecked"),
  javacOptions in (Compile, doc) ++= Seq("-Xdoclint:none"),
  javaOptions in test += "-Dsmile.threads=1",
  libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.30" % "test",
  libraryDependencies += "junit" % "junit" % "4.13" % "test",
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test" exclude("junit", "junit-dep"),
  scalaVersion := "2.13.1",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-target:jvm-1.8"),
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
  pomIncludeRepository := { _ => false },
  pomExtra := (
    <url>https://github.com/haifengl/smile</url>
      <licenses>
        <license>
          <name>GNU Lesser General Public License, Version 3</name>
          <url>https://opensource.org/licenses/LGPL-3.0</url>
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
  publish / skip := true
)

lazy val root = project.in(file(".")).settings(nonPubishSettings: _*)
  .aggregate(core, data, io, math, netlib, nd4j, graph, interpolation, nlp, plot, json, vega, demo, benchmark, scala, cas, shell)

lazy val math = project.in(file("math")).settings(commonSettings: _*)

lazy val nd4j = project.in(file("nd4j")).settings(nonPubishSettings: _*).dependsOn(math)

lazy val netlib = project.in(file("netlib")).settings(commonSettings: _*).dependsOn(math)

lazy val data = project.in(file("data")).settings(commonSettings: _*).dependsOn(math)

lazy val io = project.in(file("io")).settings(commonSettings: _*).dependsOn(data)

lazy val graph = project.in(file("graph")).settings(commonSettings: _*).dependsOn(math)

lazy val interpolation = project.in(file("interpolation")).settings(commonSettings: _*).dependsOn(math)

lazy val core = project.in(file("core")).settings(commonSettings: _*).dependsOn(data, math, graph, netlib, io % "test")

lazy val nlp = project.in(file("nlp")).settings(commonSettings: _*).dependsOn(core)

lazy val plot = project.in(file("plot")).settings(commonSettings: _*).dependsOn(core)

lazy val demo = project.in(file("demo")).settings(nonPubishSettings: _*).dependsOn(core, io, interpolation, plot)

lazy val benchmark = project.in(file("benchmark")).settings(nonPubishSettings: _*).dependsOn(core, scala)

lazy val json = project.in(file("json")).settings(commonSettings: _*)

lazy val vega = project.in(file("vega")).settings(commonSettings: _*).dependsOn(json, data)

lazy val scala = project.in(file("scala")).settings(commonSettings: _*).dependsOn(core, io, interpolation, nlp, plot, json, vega)

lazy val cas = project.in(file("cas")).settings(commonSettings: _*)

lazy val shell = project.in(file("shell")).settings(nonPubishSettings: _*).dependsOn(benchmark, demo, cas, scala)
