name := "smile"

lazy val scala213 = "2.13.18"
lazy val scala3 = "3.3.7"
lazy val supportedScalaVersions = List(scala213, scala3)
lazy val os = sys.props.get("os.name").get.toLowerCase.split(" ")(0)

lazy val commonSettings = Seq(
  resolvers += "Akka library repository" at "https://repo.akka.io/maven",
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",

  // skip packageDoc task on stage
  Compile / packageDoc / mappings := Seq(),
  // always set scala version including Java only modules
  scalaVersion := scala213,

  description := "Statistical Machine Intelligence and Learning Engine",
  organization := "com.github.haifengl",
  organizationName := "Haifeng Li",
  organizationHomepage := Some(url("https://haifengl.github.io/")),
  version := "5.0.1",

  // Run in a separate JVM, to make sure sbt waits until all threads have
  // finished before returning.
  // If you want to keep the application running while executing other
  // sbt tasks, consider https://github.com/spray/sbt-revolver/
  fork := true,

  autoAPIMappings := true,
  Test / baseDirectory := (ThisBuild/Test/run/baseDirectory).value,
  Test / parallelExecution := false,
  Test / publishArtifact := false,
  Test / javaOptions ++= Seq(
    "-XX:+UseG1GC",
    "-XX:MaxMetaspaceSize=1024M",
    "-Xss4M",
    "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug",
    //"--add-opens=java.base/java.lang=ALL-UNNAMED",
    //"--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    //"--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    //"--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
    //"--add-opens=java.base/sun.security.action=ALL-UNNAMED",
    "--enable-native-access=ALL-UNNAMED"
  ),
  Test / envVars := Map(
    os match {
      case "windows" =>
        "PATH" -> s"${(Test / baseDirectory).value}/shell/src/universal/bin;${System.getenv("PATH")}"
      case "mac" =>
        "DYLD_LIBRARY_PATH" -> s"${(Test / baseDirectory).value}/shell/src/universal/bin:${System.getenv("DYLD_LIBRARY_PATH")}"
      case _ =>
        "LD_LIBRARY_PATH" -> s"${(Test / baseDirectory).value}/shell/src/universal/bin:${System.getenv("LD_LIBRARY_PATH")}"
    }
  ),

  versionScheme := Some("early-semver"),
  publishTo := {
    val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
    if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
    else localStaging.value
  },
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
  homepage := Some(url("https://haifengl.github.io/")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/haifengl/smile"),
      "scm:git:git@github.com:haifengl/smile.git"
    )
  ),
  developers := List(
    Developer(
      id = "haifengl",
      name = "Haifeng Li",
      email = "",
      url = url("https://github.com/haifengl")
    ),
    Developer(
      id = "kklioss",
      name = "Karl Li",
      email = "",
      url = url("https://github.com/kklioss")
    )
  ),
  licenses := List(
    "GNU 3" -> url("https://opensource.org/licenses/GPL-3.0")
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
    "-source", "25",
    "-target", "25"
  ),
  Compile / doc / javacOptions ++= Seq(
    //"-Xdoclint:none",
    "--allow-script-in-comments",
    "-doctitle", """Smile &mdash; Statistical Machine Intelligence &amp; Learning Engine""",
    "--add-script", "project/gtag.js",
    "-bottom", """Copyright &copy; 2010-2025 Haifeng Li. All rights reserved.
                 |Use is subject to <a href="https://raw.githubusercontent.com/haifengl/smile/master/LICENSE">license terms.</a>
                 |<script async src="https://www.googletagmanager.com/gtag/js?id=G-57GD08QCML"></script>""".stripMargin
  ),
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % "2.0.17",
    "org.slf4j" % "slf4j-simple" % "2.0.17" % Test,
    "org.junit.jupiter" % "junit-jupiter-engine" % "6.0.1" % Test,
    "com.github.sbt.junit" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test
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
    "-release:25"
  ),
  scalacOptions ++= Seq(
    if (scalaVersion.value.startsWith("2.13")) "-Xsource:3"
    else "-source:3.0"
  ),
  Compile / doc / scalacOptions ++= Seq(
    "-groups",
    "-dynamic-side-menu",
    "-project-version", "4.3.0",
    "-project-logo", "web/src/images/smile.jpg",
    "-project-footer", """Copyright © 2010-2025 Haifeng Li. All rights reserved.
                         |Use is subject to license terms.""".stripMargin
  ),
  libraryDependencies ++= Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
    "org.slf4j" % "slf4j-simple" % "2.0.17" % Test,
    "org.specs2" %% "specs2-core" % "4.23.0" % Test
  ),
)

lazy val javaCppSettings = Seq(
  libraryDependencies ++= Seq(
    "org.bytedeco" % "javacpp"   % "1.5.12"        classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "openblas"  % "0.3.30-1.5.12" classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "arpack-ng" % "3.9.1-1.5.12"  classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64" classifier ""
  )
)

lazy val akkaSettings = Seq(
  libraryDependencies ++= {
    val akkaVersion     = "2.9.3"
    val akkaHttpVersion = "10.6.3"
    Seq(
      "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
      "com.typesafe.akka" %% "akka-http"                % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json"     % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test
    )
  }
)

JavaUnidoc / unidoc / javacOptions ++= Seq(
  "-Xdoclint:none",
  "--allow-script-in-comments",
  "-doctitle", """Smile &mdash; Statistical Machine Intelligence &amp; Learning Engine""",
  "--add-script", "project/gtag.js",
  "-bottom", """Copyright &copy; 2010-2025 Haifeng Li. All rights reserved.
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
  .aggregate(core, base, nlp, deep, plot, json, scala, kotlin, shell, serve)

lazy val base = project.in(file("base"))
  .settings(javaSettings: _*)

lazy val core = project.in(file("core"))
  .settings(javaSettings: _*)
  .dependsOn(base % "provided->provided;compile->compile;test->test;runtime->runtime")

lazy val deep = project.in(file("deep"))
  .settings(javaSettings: _*)
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
  .settings(publish / skip := true)
  .dependsOn(core)

lazy val kotlin = project.in(file("kotlin"))
  .settings(commonSettings: _*)
  .enablePlugins(KotlinPlugin)
  .dependsOn(core, nlp)

lazy val shell = project.in(file("shell"))
  .settings(javaSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publish / skip := true)
  .dependsOn(scala)

lazy val serve = project.in(file("serve"))
  .settings(scalaSettings: _*)
  .settings(publish / skip := true)
  .dependsOn(deep)
