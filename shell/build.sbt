name := "smile-shell"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true
autoScalaLibrary := true

mainClass in Compile := Some("smile.shell.Main")

// native packager
enablePlugins(JavaAppPackaging)
maintainer := "Haifeng Li <haifeng.hli@gmail.com>"
packageName := "smile"
packageSummary := "Statistical Machine Intelligence and Learning Engine"
packageDescription :=
  """
    |
    |""".stripMargin

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
  "org.scala-lang" % "scala-compiler" % "2.13.2",
  "org.slf4j" % "slf4j-simple" % "1.7.30"
)
