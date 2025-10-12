name := "smile-serve"

// SprayJsonSupport not working for Future[Seq[Obj]] with Scala 3
// https://github.com/akka/akka-http/issues/3962

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
  "com.typesafe.slick" %% "slick" % "3.6.1",
  "org.xerial"         %  "sqlite-jdbc" % "3.50.3.0",
  "ch.qos.logback"     %  "logback-classic" % "1.5.19"
)

val os = sys.props.get("os.name").get.toLowerCase.split(" ")(0)
val gpu = Seq(
  "org.bytedeco" % "pytorch-platform-gpu" % "2.7.1-1.5.12",
  "org.bytedeco" % "cuda" % "12.9-9.10-1.5.12" classifier s"$os-x86_64"
)

libraryDependencies ++= (
  os match {
    case "linux" | "windows" => gpu
    case _ => Seq.empty
  }
)

libraryDependencies ++= {
  val akkaVersion     = "2.9.5"
  val akkaHttpVersion = "10.6.3"
  Seq(
    "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
    "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
    "com.typesafe.akka" %% "akka-pki"                 % akkaVersion,
    "com.typesafe.akka" %% "akka-http"                % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json"     % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
    "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test
  )
}
