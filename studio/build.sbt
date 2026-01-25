name := "smile-studio"

Compile / mainClass := Some("smile.Main")

// native packager
enablePlugins(JavaAppPackaging)
maintainer := "Haifeng Li <haifeng.hli@gmail.com>"
packageName := "smile"
packageSummary := "Statistical Machine Intelligence and Learning Engine"
packageDescription :=
  """
    |SMILE is a fast and comprehensive machine learning, NLP, linear algebra,
    |graph, interpolation, and visualization system in Java and Scala.
    |With advanced data structures and algorithms, SMILE delivers
    |state-of-art performance. SMILE is well documented and please check out
    |the project website for programming guides and more information.
    |""".stripMargin

import com.typesafe.sbt.packager.MappingsHelper._
Universal / mappings ++= Seq(
  (baseDirectory.value / "README.md") -> "README.md",
  (baseDirectory.value / "../COPYING") -> "COPYING",
  (baseDirectory.value / "../LICENSE") -> "LICENSE"
)
Universal / mappings ++= contentOf("serve/build/quarkus-app/")
  .map {
    case (file, path) => (file, s"serve/$path")
  }
Universal / mappings ++= contentOf("base/src/test/resources/data/")
  .filter {
    case (file, path) => path.startsWith("mnist") ||
      path.startsWith("usps") ||
      path.startsWith("sqlite") ||
      path.startsWith("kylo") ||
      path.startsWith("weka") ||
      path.startsWith("libsvm") ||
      path.startsWith("regression") ||
      path.startsWith("sas") ||
      path.startsWith("stat") ||
      path.startsWith("sparse") ||
      path.startsWith("matrix")
  }
  .map {
    case (file, path) => (file, s"data/$path")
  }

// dealing with long classpaths
scriptClasspath := Seq("*")

executableScriptName := "smile"
bashScriptConfigLocation := Some("${app_home}/../conf/smile.ini")
batScriptConfigLocation := Some("%APP_HOME%\\conf\\smile.ini")

bashScriptExtraDefines ++= Seq(
  """addJava "-XX:MaxMetaspaceSize=1024M"""",
  """addJava "-Xss4M"""",
  """addJava "--add-opens=java.base/java.nio=ALL-UNNAMED"""",
  """addJava "--enable-native-access=ALL-UNNAMED"""",
  """addJava "-Dsmile.home=${app_home}/.."""",
  """addJava "-Dscala.usejavacp=true"""", // for Scala REPL
  """addJava "-Dscala.repl.autoruncode=${app_home}/predef.sc""""
)

batScriptExtraDefines ++= Seq(
  """call :add_java -XX:MaxMetaspaceSize=1024M""",
  """call :add_java -Xss4M""",
  """call :add_java --add-opens=java.base/java.nio=ALL-UNNAMED""",
  """call :add_java --enable-native-access=ALL-UNNAMED""",
  """call :add_java -Dsmile.home=%APP_HOME%""",
  """call :add_java -Dscala.usejavacp=true""",
  """call :add_java -Dscala.repl.autoruncode=%APP_HOME%\bin\predef.sc""",
  """set OPENBLAS_NO_AVX512=1""",
  """set OPENBLAS_NUM_THREAD=1""",
  """set "PATH=%~dp0;!PATH!""""
)

libraryDependencies ++= Seq(
  "org.scala-lang"   %% "scala3-compiler"    % scalaVersion.value,
  "info.picocli"      % "picocli"            % "4.7.7",
  "org.slf4j"         % "slf4j-simple"       % "2.0.17",
  "com.openai"        % "openai-java"        % "4.16.1",
  "com.anthropic"     % "anthropic-java"     % "2.11.1",
  "com.google.genai"  % "google-genai"       % "1.36.0",
  "org.commonmark"    % "commonmark"         % "0.27.1",
  "org.xhtmlrenderer" % "flying-saucer-core" % "10.0.6",
  "com.fifesoft"      % "rsyntaxtextarea"    % "3.6.1",
  "com.formdev"       % "flatlaf"            % "3.7",
  "com.formdev"       % "flatlaf-fonts-jetbrains-mono" % "2.304",
  "org.apache.maven"  % "maven-resolver-provider" % "3.9.12",
  "org.apache.maven.resolver" % "maven-resolver-supplier-mvn4" % "2.0.14"
)

libraryDependencies ++= {
  val arrowV = "18.3.0"
  Seq(
    "org.apache.arrow" % "arrow-dataset" % arrowV,
    "org.apache.arrow" % "arrow-memory-netty" % arrowV,
    "org.apache.avro" % "avro" % "1.12.1" exclude("org.slf4j", "slf4j-log4j12"),
    "org.xerial.snappy" % "snappy-java" % "1.1.10.8", // for avro
    "com.epam" % "parso" % "2.0.14", // SAS7BDAT
  )
}
