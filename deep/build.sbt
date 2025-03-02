name := "smile-deep"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.deep")

Test / envVars += ("PYTORCH_ENABLE_MPS_FALLBACK" -> "1")

Test / javaOptions ++= Seq(
  "-Dorg.bytedeco.javacpp.pathsFirst=true",
  "-Djava.library.path=serve/src/universal/torch/lib"
)

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-xml" % "2.18.3",
  "ai.djl.sentencepiece" % "sentencepiece" % "0.32.0",
  "org.bytedeco" % "pytorch-platform" % "2.5.1-1.5.11",
  "org.bytedeco" % "cuda-platform"    % "12.6-9.5-1.5.11"
)

val os = sys.props.get("os.name").get.toLowerCase.split(" ")(0)
val gpu = Seq(
  "org.bytedeco" % "pytorch" % "2.5.1-1.5.11" % Provided classifier s"$os-x86_64-gpu",
  "org.bytedeco" % "cuda" % "12.6-9.5-1.5.11" % Provided classifier s"$os-x86_64-redist"
)

libraryDependencies ++= (
  os match {
    case "linux" | "windows" => gpu
    case _ => Seq.empty
  }
)
