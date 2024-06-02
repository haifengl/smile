name := "smile-deep"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.deep")

Test / envVars += ("PYTORCH_ENABLE_MPS_FALLBACK" -> "1")

Test / javaOptions ++= Seq(
  "-Dorg.bytedeco.javacpp.pathsFirst=true",
  "-Djava.library.path=deep/lib/torch/lib"
)

libraryDependencies ++= Seq(
  "ai.djl.sentencepiece" % "sentencepiece" % "0.28.0",
  "org.bytedeco"   % "pytorch-platform"    % "2.3.0-1.5.11-SNAPSHOT",
  "org.bytedeco"   % "cuda-platform"    % "12.3-8.9-1.5.11-SNAPSHOT" classifier ""
)

val os = sys.props.get("os.name").get.toLowerCase.split(" ")(0)
val gpu = Seq(
  "org.bytedeco"   % "pytorch" % "2.3.0-1.5.11-SNAPSHOT" % Provided classifier s"$os-x86_64-gpu",
  "org.bytedeco"   % "cuda" % "12.3-8.9-1.5.11-SNAPSHOT" % Provided classifier s"$os-x86_64" classifier s"$os-x86_64-redist"
)

libraryDependencies ++= (
  os match {
    case "linux" | "windows" => gpu
    case _ => Seq.empty
  }
)