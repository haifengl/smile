name := "smile-serve"

// SprayJsonSupport not working for Future[Seq[Obj]] with Scala 3
// https://github.com/akka/akka-http/issues/3962
scalaVersion := "3.3.4"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.serve")

Compile / mainClass := Some("smile.serve.Main")

// native packager
enablePlugins(JavaAppPackaging)
maintainer := "Karl Li <kkli@umich.edu>"
packageName := "smile-serve"
packageSummary := "LLM Serving by SMILE"

// dealing with long classpaths
scriptClasspath := Seq("*")

executableScriptName := "smile-serve"
bashScriptConfigLocation := Some("${app_home}/../conf/smile-serve.ini")
batScriptConfigLocation := Some("%APP_HOME%\\conf\\smile-serve.ini")

bashScriptExtraDefines ++= Seq(
  """addJava "-Dsmile.home=${app_home}/.."""",
  """addJava "-Dscala.usejavacp=true"""",
  """addJava "-Dorg.bytedeco.javacpp.pathsFirst=true"""",
  """addJava "-Djava.library.path=${app_home}/../torch/lib""""
)

batScriptExtraDefines ++= Seq(
  """call :add_java -Dsmile.home=%APP_HOME%""",
  """call :add_java -Dscala.usejavacp=true""",
  """call :add_java -Djava.library.path=%APP_HOME%\bin""",
  """set OPENBLAS_NO_AVX512=1""",
  """set OPENBLAS_NUM_THREAD=1""",
  """set PATH=!PATH!;%~dp0"""
)

libraryDependencies ++= Seq(
  "com.github.scopt"   %% "scopt" % "4.1.0",
  "com.typesafe.slick" %% "slick" % "3.5.2",
  "org.xerial"         %  "sqlite-jdbc" % "3.49.1.0",
  "ch.qos.logback"     %  "logback-classic" % "1.5.17"
)

val os = sys.props.get("os.name").get.toLowerCase.split(" ")(0)
val gpu = Seq(
  "org.bytedeco" % "pytorch" % "2.5.1-1.5.11" classifier s"$os-x86_64-gpu",
  "org.bytedeco" % "cuda" % "12.6-9.5-1.5.11" classifier s"$os-x86_64-redist"
)

libraryDependencies ++= (
  os match {
    case "linux" | "windows" => gpu
    case _ => Seq.empty
  }
)
