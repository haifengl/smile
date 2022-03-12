name := "smile"

lazy val commonSettings = Seq(
  // skip packageDoc task on stage
  Compile / packageDoc / mappings := Seq(),
  // skip javadoc and scaladoc for publishLocal
  Compile / packageDoc / publishArtifact := false,
  // always set scala version including Java only modules
  scalaVersion := "2.13.8",

  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("http://haifengl.github.io/")),
  version := "3.0.0",

  Test / parallelExecution := false,
  autoAPIMappings := true,

  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  Test / publishArtifact := false,
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
  pomExtra := (
    <url>https://github.com/haifengl/smile</url>
      <licenses>
        <license>
          <name>GNU General Public License, Version 3</name>
          <url>https://opensource.org/licenses/GPL-3.0</url>
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
          <url>https://haifengl.github.io/</url>
        </developer>
      </developers>
  )
)

lazy val javaSettings = commonSettings ++ Seq(
  crossPaths := false,
  autoScalaLibrary := false,
  Compile / compile / javacOptions ++= Seq(
    "-encoding", "UTF8",
    "-g:lines,vars,source",
    "-Xlint:deprecation",
    "-Xlint:unchecked"
  ),
  Compile / doc / javacOptions ++= Seq(
    "-Xdoclint:none",
    "--allow-script-in-comments",
    "-doctitle", """Smile &mdash; Statistical Machine Intelligence and Learning Engine""",
    "-bottom", """<script src="{@docRoot}/../../js/google-analytics.js" type="text/javascript"></script>"""
    ),
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % "1.7.36",
    "org.slf4j" % "slf4j-simple" % "1.7.36" % Test,
    "junit" % "junit" % "4.13.2" % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test exclude("junit", "junit-dep")
  ),
  Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a"))
)

lazy val java8Settings = javaSettings ++ Seq(
  Compile / compile / javacOptions ++= Seq(
    "-source", "1.8",
    "-target", "1.8"
  ),
)

lazy val java17Settings = javaSettings ++ Seq(
  Compile / compile / javacOptions ++= Seq(
    "-source", "17",
    "-target", "17",
    "--enable-preview",
    "-Xlint:preview"
  ),
  Compile / doc / javacOptions ++= Seq(
    "--enable-preview"
  )
)

lazy val scalaSettings = commonSettings ++ Seq(
  crossPaths := true,
  autoScalaLibrary := true,
  scalacOptions := Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-encoding", "utf8",
    "-target:jvm-1.8"
  ),
  Compile / doc / scalacOptions ++= Seq(
    "-groups",
    "-implicits"
  ),
  libraryDependencies ++= Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
    "org.slf4j" % "slf4j-simple" % "1.7.36" % Test,
    "org.specs2" %% "specs2-core" % "4.14.1" % Test
  ),
)

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .enablePlugins(JavaUnidocPlugin)
  .settings(
    JavaUnidoc / unidoc / unidocProjectFilter := inAnyProject -- inProjects(json, scala, spark, shell, plot)
  )
  .aggregate(core, base, mkl, nlp, plot, json, scala, spark, shell)

lazy val base = project.in(file("base")).settings(java8Settings: _*)

lazy val mkl = project.in(file("mkl"))
  .settings(java8Settings: _*)
  .dependsOn(base)

lazy val core = project.in(file("core"))
  .settings(java8Settings: _*)
  .dependsOn(base % "compile->compile;test->test")

lazy val deep = project.in(file("deep"))
  .settings(java8Settings: _*)
  .settings(publish / skip := true)

lazy val nlp = project.in(file("nlp"))
  .settings(java8Settings: _*)
  .dependsOn(core)

lazy val plot = project.in(file("plot"))
  .settings(java8Settings: _*)
  .dependsOn(base)

lazy val json = project.in(file("json")).settings(scalaSettings: _*)

lazy val scala = project.in(file("scala"))
  .settings(scalaSettings: _*)
  .dependsOn(core, nlp, plot, json)

lazy val spark = project.in(file("spark"))
  .settings(scalaSettings: _*)
  .settings(publish / skip := true)
  .dependsOn(core)

lazy val shell = project.in(file("shell"))
  .settings(scalaSettings: _*)
  .settings(publish / skip := true)
  .dependsOn(scala)
