// BuildInfo

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
  autoScalaLibrary := false
)

// SBT native packager
enablePlugins(JavaAppPackaging)

lazy val root = project.in(file("."))
  .settings(
    commonSettings ++ Seq(
      name := "smile",
      maintainer := "Haifeng Li <haifeng.hli@gmail.com>",
      packageName := "smile",
      packageSummary := "SMILE",
      packageDescription := "Statistical Machine Intelligence and Learning Engine",
      executableScriptName := "smile",
      bashScriptExtraDefines += """addJava "-Dsmile.home=${app_home}"""",
      bashScriptExtraDefines += """addJava "-Dscala.repl.autoruncode=${app_home}/init.scala"""",
      mainClass in Compile := Some("smile.shell.Shell")
    ): _*
  )
  .aggregate(core, data, math, graph, plot, interpolation, nlp, demo, benchmark, scala, shell)
  .dependsOn(core, data, math, graph, plot, interpolation, nlp, demo, benchmark, scala, shell)

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
