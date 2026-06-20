name := "smile-studio"

resolvers += "jitpack" at "https://jitpack.io"

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
  """export SMILE_HOME=$(dirname "${app_home}")""",
  """addJava "-XX:MaxMetaspaceSize=1024M"""",
  """addJava "-Xss4M"""",
  """addJava "--add-opens=java.base/java.nio=ALL-UNNAMED"""",
  """addJava "--enable-native-access=ALL-UNNAMED"""",
  """addJava "-Dsmile.home=${SMILE_HOME}"""",
  """addJava "-Dscala.usejavacp=true"""", // for Scala REPL
  """addJava "-Dscala.repl.autoruncode=${app_home}/predef.sc"""",
  """export PYTHONPATH="${PYTHONPATH}:${app_home}/../lib/ioa-agent-1.0.0.jar"""",
  """export PYTHONUTF8=1""",
  """source "$SMILE_HOME/venv/bin/activate"""",
  """export LD_LIBRARY_PATH=${app_home}/../venv/Lib/site-packages/torch/lib:${app_home}/../venv/Lib/site-packages/onnxruntime/capi:$LD_LIBRARY_PATH"""
)

batScriptExtraDefines ++= Seq(
  """call :add_java -XX:MaxMetaspaceSize=1024M""",
  """call :add_java -Xss4M""",
  """call :add_java --add-opens=java.base/java.nio=ALL-UNNAMED""",
  """call :add_java --enable-native-access=ALL-UNNAMED""",
  """call :add_java --enable-preview""",
  """call :add_java -Dsmile.home=%APP_HOME%""",
  """call :add_java -Dscala.usejavacp=true""",
  """call :add_java -Dscala.repl.autoruncode=%APP_HOME%\bin\predef.sc""",
  """set "JAVA_HOME=%APP_HOME%\jbr"""",
  """set OPENBLAS_NO_AVX512=1""",
  """set OPENBLAS_NUM_THREAD=1""",
  """set PYTHONPATH=%PYTHONPATH%;%APP_HOME%\lib\ioa-agent-1.0.0.jar""",
  """set PYTHONUTF8=1""",
  """CALL "%APP_HOME%\venv\Scripts\activate.bat"""",
  // activate.bat will set PATH with %PATH%, which is processed during parsing
  // and thus does not include the new entries. We need to set PATH afterward
  // to make sure the new entries are included.
  """set "PATH=%~dp0;%APP_HOME%\venv\Lib\site-packages\torch\lib;!PATH!""""
)

libraryDependencies ++= Seq(
  "org.scala-lang"   %% "scala3-compiler"    % scalaVersion.value,
  "info.picocli"      % "picocli"            % "4.7.7",
  "ch.qos.logback"    % "logback-classic"    % "1.5.34",
  "com.openai"        % "openai-java"        % "4.41.0",
  "com.anthropic"     % "anthropic-java"     % "2.42.0",
  "com.google.genai"  % "google-genai"       % "1.53.0",
  "org.commonmark"    % "commonmark"         % "0.29.0",
  "org.xhtmlrenderer" % "flying-saucer-core" % "10.3.0",
  "org.eclipse.lsp4j" % "org.eclipse.lsp4j"  % "1.0.0",
  "com.fifesoft"      % "rsyntaxtextarea"    % "3.6.3",
  "com.fifesoft"      % "rstaui"             % "3.3.2",
  "com.fifesoft"      % "spellchecker"       % "3.4.1",
  "com.formdev"       % "flatlaf"            % "3.7.1",
  "com.formdev"       % "flatlaf-fonts-jetbrains-mono" % "2.304",
  "org.apache.maven"  % "maven-resolver-provider" % "3.9.16",
  "org.apache.maven.resolver"   % "maven-resolver-supplier-mvn4" % "2.0.18",
  "io.modelcontextprotocol.sdk" % "mcp"          % "2.0.0",
  "io.github.furstenheim"       % "copy_down"    % "1.1",
  "org.jsoup"                   % "jsoup"        % "1.22.2",
  "com.github.serpapi"          % "serpapi-java" % "1.1.0",
  "com.google.code.gson"        % "gson"         % "2.14.0" // evict older version used by serpapi
)

libraryDependencies ++= {
  val jacksonV = "3.2.0"
  Seq(
    "tools.jackson.core"       % "jackson-databind"            % jacksonV,
    "tools.jackson.dataformat" % "jackson-dataformat-yaml"     % jacksonV,
    "com.github.victools"      % "jsonschema-generator"        % "4.38.0",
    "com.github.victools"      % "jsonschema-module-jackson"   % "4.38.0",
    "com.github.victools"      % "jsonschema-module-swagger-2" % "4.38.0"
  )
}

libraryDependencies ++= {
  val arrowV = "19.0.0"
  Seq(
    "org.apache.arrow"   % "arrow-dataset"       % arrowV,
    "org.apache.arrow"   % "arrow-memory-unsafe" % arrowV,
    "org.apache.avro"    % "avro"                % "1.12.1" exclude("org.slf4j", "slf4j-log4j12"),
    "org.xerial.snappy"  % "snappy-java"         % "1.1.10.8", // for avro
    "com.epam"           % "parso"               % "2.0.14"    // SAS7BDAT
  )
}

