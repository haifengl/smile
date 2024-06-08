name := "smile"

lazy val scala213 = "2.13.14"
lazy val scala3 = "3.3.3"
lazy val supportedScalaVersions = List(scala213, scala3)

lazy val commonSettings = Seq(
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",

  // skip packageDoc task on stage
  Compile / packageDoc / mappings := Seq(),
  // always set scala version including Java only modules
  scalaVersion := scala213,

  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("https://haifengl.github.io/")),
  version := "3.1.0",

  autoAPIMappings := true,
  Test / fork := true,
  Test / baseDirectory := (ThisBuild/Test/run/baseDirectory).value,
  Test / parallelExecution := false,
  Test / publishArtifact := false,
  Test / javaOptions ++= Seq(
    "-XX:+UseG1GC",
    "-XX:MaxMetaspaceSize=1024M",
    "-Xss4M",
    "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
  ),

  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
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
    "-Xlint:unchecked",
    "-source", "17",
    "-target", "17"
  ),
  Compile / doc / javacOptions ++= Seq(
    "-Xdoclint:none",
    "--allow-script-in-comments",
    "-doctitle", """Smile &mdash; Statistical Machine Intelligence &amp; Learning Engine""",
    "--add-script", "project/gtag.js",
    "-bottom", """Copyright &copy; 2010-2024 Haifeng Li. All rights reserved.
                 |Use is subject to <a href="https://raw.githubusercontent.com/haifengl/smile/master/LICENSE">license terms.</a>
                 |<script async src="https://www.googletagmanager.com/gtag/js?id=G-57GD08QCML"></script>""".stripMargin
  ),
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % "2.0.13",
    "org.slf4j" % "slf4j-simple" % "2.0.13" % Test,
    "org.junit.jupiter" % "junit-jupiter-engine" % "5.10.2" % Test,
    "net.aichler"       % "jupiter-interface"    % "0.11.1" % Test
  )
)

lazy val scalaSettings = commonSettings ++ Seq(
  crossPaths := true,
  autoScalaLibrary := true,
  crossScalaVersions := supportedScalaVersions,
  scalacOptions := Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-encoding", "utf8",
    "-release:17"
  ),
  Compile / doc / scalacOptions ++= Seq(
    "-groups",
    "-dynamic-side-menu",
    "-project-version", "3.1.0",
    "-project-logo", "web/images/smile.jpg",
    "-project-footer", """Copyright © 2010-2024 Haifeng Li. All rights reserved.
                         |Use is subject to license terms.""".stripMargin
  ),
  libraryDependencies ++= Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    "org.slf4j" % "slf4j-simple" % "2.0.13" % Test,
    "org.specs2" %% "specs2-core" % "4.20.5" % Test
  ),
)

lazy val javaCppSettings = Seq(
  libraryDependencies ++= Seq(
    "org.bytedeco" % "javacpp"   % "1.5.11-SNAPSHOT"        classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "openblas"  % "0.3.27-1.5.11-SNAPSHOT" classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "arpack-ng" % "3.9.1-1.5.11-SNAPSHOT"  classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64" classifier ""
  )
)

JavaUnidoc / unidoc / javacOptions ++= Seq(
  "-Xdoclint:none",
  "--allow-script-in-comments",
  "-doctitle", """Smile &mdash; Statistical Machine Intelligence &amp; Learning Engine""",
  "--add-script", "project/gtag.js",
  "-bottom", """Copyright &copy; 2010-2024 Haifeng Li. All rights reserved.
               |Use is subject to <a href="https://raw.githubusercontent.com/haifengl/smile/master/LICENSE">license terms.</a>
               |<script async src="https://www.googletagmanager.com/gtag/js?id=G-57GD08QCML"></script>""".stripMargin
)

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .enablePlugins(JavaUnidocPlugin)
  .settings(publish / skip := true)
  .settings(crossScalaVersions := Nil)
  .settings(
    JavaUnidoc / unidoc / unidocProjectFilter := inAnyProject -- inProjects(json, scala, spark, shell)
  )
  .aggregate(core, base, nlp, deep, plot, json, scala, spark, shell, serve)

lazy val base = project.in(file("base"))
  .settings(javaSettings: _*)
  .settings(javaCppSettings: _*)

lazy val core = project.in(file("core"))
  .settings(javaSettings: _*)
  .dependsOn(base % "provided->provided;compile->compile;test->test;runtime->runtime")

lazy val deep = project.in(file("deep"))
  .settings(javaSettings: _*)
  .settings(publish / skip := true)
  .dependsOn(base)

lazy val nlp = project.in(file("nlp"))
  .settings(javaSettings: _*)
  .dependsOn(core)

lazy val plot = project.in(file("plot"))
  .settings(javaSettings: _*)
  .dependsOn(base)

lazy val json = project.in(file("json"))
  .settings(scalaSettings: _*)

lazy val scala = project.in(file("scala"))
  .settings(scalaSettings: _*)
  .dependsOn(core, nlp, plot, json)

lazy val spark = project.in(file("spark"))
  .settings(scalaSettings: _*)
  .dependsOn(core)

lazy val shell = project.in(file("shell"))
  .settings(scalaSettings: _*)
  .settings(publish / skip := true)
  .dependsOn(scala)

lazy val serve = project.in(file("serve"))
  .settings(scalaSettings: _*)
  .settings(publish / skip := true)
  .dependsOn(deep)
