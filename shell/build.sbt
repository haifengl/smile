name := "smile-shell"

Compile / mainClass := Some("smile.shell.Main")

// native packager
enablePlugins(JavaAppPackaging)
maintainer := "Haifeng Li <haifeng.hli@gmail.com>"
packageName := "smile"
packageSummary := "Statistical Machine Intelligence and Learning Engine"
packageDescription :=
  """
    |Smile is a fast and comprehensive machine learning, NLP, linear algebra,
    |graph, interpolation, and visualization system in Java and Scala.
    |With advanced data structures and algorithms, Smile delivers
    |state-of-art performance. Smile is well documented and please check out
    |the project website for programming guides and more information.
    |""".stripMargin

// Filter data files in universal
Universal / mappings := {
  // universalMappings: Seq[(File,String)]
  val universalMappings = (Universal / mappings).value

  // removing means filtering
  universalMappings filter {
    case (file, name) => !name.startsWith("data/kylo") &&
                         !name.startsWith("data/matrix") &&
                         !name.startsWith("data/nlp") &&
                         !name.startsWith("data/sas") &&
                         !name.startsWith("data/sparse") &&
                         !name.startsWith("data/sqlite") &&
                         !name.startsWith("data/transaction") &&
                         !name.startsWith("data/wavefront")
  }
}

// dealing with long classpaths
scriptClasspath := Seq("*")

executableScriptName := "smile"
bashScriptConfigLocation := Some("${app_home}/../conf/smile.ini")
batScriptConfigLocation := Some("%APP_HOME%\\conf\\smile.ini")

bashScriptExtraDefines ++= Seq(
  """addJava "-Dsmile.home=${app_home}/.."""",
  """addJava "-Dscala.usejavacp=true"""", // for Scala REPL
  """addJava "-Dscala.repl.autoruncode=${app_home}/predef.sc"""",
  """addJava "-Dconfig.file=${app_home}/../conf/smile.conf""""
)

batScriptExtraDefines ++= Seq(
  """call :add_java -Dsmile.home=%APP_HOME%""",
  """call :add_java -Dscala.usejavacp=true""",
  """call :add_java -Dscala.repl.autoruncode=%APP_HOME%\bin\predef.sc""",
  """call :add_java -Dconfig.file=%APP_HOME%\conf\smile.conf""",
  """call :add_java -Djava.library.path=%APP_HOME%\bin""",
  """set PATH=!PATH!;%~dp0"""
)

// BuildInfo
enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "smile.shell"
buildInfoOptions += BuildInfoOption.BuildTime

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "4.1.0",
  "org.scala-lang" % "scala-compiler" % "2.13.13",
  "org.slf4j" % "slf4j-simple" % "2.0.12",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
  "com.typesafe.akka" %% "akka-stream" % "2.8.5",
  "com.typesafe.akka" %% "akka-http" % "10.5.3",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.3",
  "com.lightbend.akka" %% "akka-stream-alpakka-csv" % "6.0.2"
)
