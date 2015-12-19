lazy val commonSettings = Seq(
  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("https://github.com/haifengl/smile")),
  version := "1.0.4",
  javacOptions in (Compile, compile) ++= Seq("-source", "1.7", "-target", "1.7", "-g:lines"),
  javacOptions in (Compile, doc) ++= Seq("-Xdoclint:none"),
  crossPaths := false,
  autoScalaLibrary := false
)

lazy val root = project.in(file(".")).aggregate(core, data, math, graph, plot, interpolation, nlp, demo)

lazy val math = project.in(file("math")).settings(commonSettings: _*)

lazy val core = project.in(file("core")).settings(commonSettings: _*).dependsOn(data, math, graph)

lazy val data = project.in(file("data")).settings(commonSettings: _*).dependsOn(math)

lazy val graph = project.in(file("graph")).settings(commonSettings: _*).dependsOn(math)

lazy val interpolation = project.in(file("interpolation")).settings(commonSettings: _*).dependsOn(math)

lazy val nlp = project.in(file("nlp")).settings(commonSettings: _*).dependsOn(core)

lazy val plot = project.in(file("plot")).settings(commonSettings: _*).dependsOn(core)

lazy val demo = project.in(file("demo")).settings(commonSettings: _*).dependsOn(core, interpolation, plot)
