lazy val commonSettings = Seq(
  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("https://github.com/haifengl/smile")),
  version := "1.0.4",
  javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-g:lines"),
  javacOptions in (Compile, doc) ++= Seq("-Xdoclint:none"),
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
  scalaVersion := "2.11.7",
  parallelExecution in Test := false,
  crossPaths := false,
  autoScalaLibrary := false
)

lazy val root = project.in(file(".")).aggregate(core, data, math, graph, plot, interpolation, nlp, demo, benchmark, testData)

lazy val math = project.in(file("math")).settings(commonSettings: _*)

lazy val core = project.in(file("core")).settings(commonSettings: _*).dependsOn(data, math, graph, testData % "test")

lazy val data = project.in(file("data")).settings(commonSettings: _*).dependsOn(math, testData % "test")

lazy val graph = project.in(file("graph")).settings(commonSettings: _*).dependsOn(math)

lazy val interpolation = project.in(file("interpolation")).settings(commonSettings: _*).dependsOn(math)

lazy val nlp = project.in(file("nlp")).settings(commonSettings: _*).dependsOn(core, testData % "test")

lazy val plot = project.in(file("plot")).settings(commonSettings: _*).dependsOn(core)

lazy val demo = project.in(file("demo")).settings(commonSettings: _*).dependsOn(core, interpolation, plot)

lazy val benchmark = project.in(file("benchmark")).settings(commonSettings: _*).dependsOn(core, testData)

lazy val testData = project.in(file("test-data")).settings(commonSettings: _*)

