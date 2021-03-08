name := "smile"

lazy val commonSettings = Seq(
  // skip packageDoc task on stage
  mappings in (Compile, packageDoc) := Seq(),
  // skip javadoc and scaladoc for publishLocal
  publishArtifact in (Compile, packageDoc) := false,
  // always set scala version including Java only modules
  scalaVersion := "2.13.5",

  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("http://haifengl.github.io/")),
  version := "2.6.1",

  parallelExecution in Test := false,
  autoAPIMappings := true,

  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
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
          <url>https://haifengl.github.io/</url>
        </developer>
      </developers>
  )
)

lazy val javaSettings = commonSettings ++ Seq(
  crossPaths := false,
  autoScalaLibrary := false,
  javacOptions in (Compile, compile) ++= Seq(
    "-encoding", "UTF8",
    "-g:lines,vars,source",
    "-Xlint:deprecation",
    "-Xlint:unchecked"
  ),
  javacOptions in (Compile, doc) ++= Seq(
    "-Xdoclint:none",
    "--allow-script-in-comments",
    "-doctitle", """Smile &mdash; Statistical Machine Intelligence and Learning Engine""",
    "-bottom", """<script src="{@docRoot}/../../js/google-analytics.js" type="text/javascript"></script>"""
    ),
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-simple" % "1.7.30" % "test",
    "junit" % "junit" % "4.13.2" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test" exclude("junit", "junit-dep")
  ),
  testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a"))
)

lazy val java8Settings = javaSettings ++ Seq(
  javacOptions in (Compile, compile) ++= Seq(
    "-source", "1.8",
    "-target", "1.8"
  ),
)

lazy val java15Settings = javaSettings ++ Seq(
  javacOptions in (Compile, compile) ++= Seq(
    "-source", "15",
    "-target", "15",
    "--enable-preview",
    "-Xlint:preview"
  ),
  javacOptions in (Compile, doc) ++= Seq(
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
  scalacOptions in (Compile, doc) ++= Seq(
    "-groups",
    "-implicits"
  )
)

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .enablePlugins(JavaUnidocPlugin)
  .settings(
    unidocProjectFilter in (JavaUnidoc, unidoc) := inAnyProject -- inProjects(json, demo, scala, spark, shell, plot)
  )
  .aggregate(core, data, io, math, mkl, nlp, plot, json, demo, scala, spark, shell)

lazy val math = project.in(file("math")).settings(java8Settings: _*)

lazy val mkl = project.in(file("mkl"))
  .settings(java8Settings: _*)
  .dependsOn(math)

lazy val data = project.in(file("data"))
  .settings(java8Settings: _*)
  .dependsOn(math)

lazy val io = project.in(file("io"))
  .settings(java8Settings: _*)
  .dependsOn(data)

lazy val core = project.in(file("core"))
  .settings(java8Settings: _*)
  .dependsOn(data, math, io % "test")

lazy val deep = project.in(file("deep"))
  .settings(java8Settings: _*)
  .settings(publish / skip := true)

lazy val nlp = project.in(file("nlp"))
  .settings(java8Settings: _*)
  .dependsOn(core)

lazy val plot = project.in(file("plot"))
  .settings(java8Settings: _*)
  .dependsOn(core)

lazy val demo = project.in(file("demo"))
  .settings(java8Settings: _*)
  .settings(publish / skip := true)
  .dependsOn(core, io, plot)

lazy val json = project.in(file("json")).settings(scalaSettings: _*)

lazy val scala = project.in(file("scala"))
  .settings(scalaSettings: _*)
  .dependsOn(core, io, nlp, plot, json)

lazy val spark = project.in(file("spark"))
  .settings(scalaSettings: _*)
  .settings(publish / skip := true)
  .dependsOn(core, data, io % "test")

lazy val shell = project.in(file("shell"))
  .settings(scalaSettings: _*)
  .settings(publish / skip := true)
  .dependsOn(demo, scala)
