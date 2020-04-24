name := "smile-shell"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true

autoScalaLibrary := true

mainClass in Compile := Some("smile.shell.Main")

// In order to generate launch scripts only for specified mainClass,
// we need to discard automatically found main classes.
discoveredMainClasses in Compile := Seq()

// native packager
enablePlugins(JavaAppPackaging)
// dealing with long classpaths
scriptClasspath := Seq("*")

maintainer := "Haifeng Li <haifeng.hli@gmail.com>"

packageName := "smile"

packageSummary := "Smile"

packageDescription := "Statistical Machine Intelligence and Learning Engine"

executableScriptName := "smile"

bashScriptConfigLocation := Some("${app_home}/../conf/smile.ini")

bashScriptExtraDefines += """addJava "-Dsmile.home=${app_home}/..""""

// Scala REPL needs this
bashScriptExtraDefines += """addJava "-Dscala.usejavacp=true""""

bashScriptExtraDefines += """addJava "-Dscala.repl.autoruncode=${app_home}/predef.sc""""

bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/smile.conf""""

batScriptExtraDefines  += """set _JAVA_OPTS=!_JAVA_OPTS! -Dsmile.home=%SMILE_HOME% -Dscala.usejavacp=true -Dscala.repl.autoruncode=%SMILE_HOME%\\bin\\predef.sc -Djava.library.path=%SMILE_HOME%\\bin"""

batScriptExtraDefines  += """set PATH=!PATH!;%~dp0"""

// BuildInfo
enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "smile.shell"

buildInfoOptions += BuildInfoOption.BuildTime

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.13.2"
/*
libraryDependencies ++= Seq(
  "com.lihaoyi" %% "ammonite" % "2.0.5" cross CrossVersion.full % Provided,
  "io.github.alexarchambault.windows-ansi" % "windows-ansi" % "0.0.3" % Provided
)
*/
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.30"

