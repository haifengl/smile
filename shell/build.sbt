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
                         !name.startsWith("data/wavefront") &&
                         !name.endsWith("-ubyte")
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
  """addJava "--enable-native-access=ALL-UNNAMED"""",
  """addJava "-Dscala.repl.autoruncode=${app_home}/predef.sc""""
)

batScriptExtraDefines ++= Seq(
  """call :add_java -Dsmile.home=%APP_HOME%""",
  """call :add_java -Dscala.usejavacp=true""",
  """call :add_java -Dscala.repl.autoruncode=%APP_HOME%\bin\predef.sc""",
  """call :add_java --enable-native-access=ALL-UNNAMED""",
  """set OPENBLAS_NO_AVX512=1""",
  """set OPENBLAS_NUM_THREAD=1""",
  """set PATH=!PATH!;%APP_HOME%\bin"""
)

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt"           % "4.1.0",
  "org.scala-lang"   %  "scala-compiler"  % "2.13.17",
  "org.slf4j"        % "slf4j-simple"     % "2.0.17",
//  "ch.qos.logback"   %  "logback-classic" % "1.5.19",
//  "com.formdev"      %  "flatlaf"         % "3.6.1",
//  "com.fifesoft"     %  "rsyntaxtextarea" % "3.6.0",
//  "com.fifesoft"     %  "autocomplete"    % "3.3.2"
)

libraryDependencies ++= {
  val akkaVersion     = "2.9.5"
  val akkaHttpVersion = "10.6.3"
  Seq(
    "com.typesafe.akka"  %% "akka-actor-typed"         % akkaVersion,
    "com.typesafe.akka"  %% "akka-stream"              % akkaVersion,
    "com.typesafe.akka"  %% "akka-pki"                 % akkaVersion,
    "com.typesafe.akka"  %% "akka-http"                % akkaHttpVersion,
    "com.typesafe.akka"  %% "akka-http-spray-json"     % akkaHttpVersion,
    "com.lightbend.akka" %% "akka-stream-alpakka-csv"  % "8.0.0" exclude("com.typesafe.akka", "akka-stream_3"),
    "com.typesafe.akka"  %% "akka-actor-testkit-typed" % akkaVersion     % Test,
    "com.typesafe.akka"  %% "akka-http-testkit"        % akkaHttpVersion % Test
  )
}
