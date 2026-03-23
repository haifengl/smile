name := "smile-deep"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.deep")

Test / envVars += ("PYTORCH_ENABLE_MPS_FALLBACK" -> "1")

Test / javaOptions ++= Seq(
  "-Dorg.bytedeco.javacpp.pathsFirst=true",
  "-Djava.library.path=serve/src/universal/torch/lib"
)

libraryDependencies ++= Seq(
  "tools.jackson.core" % "jackson-databind" % "3.1.0",
  "org.bytedeco" % "pytorch-platform" % "2.10.0-1.5.13",
  "org.bytedeco" % "cuda-platform"    % "13.1-9.19-1.5.13"
)

val os = sys.props.get("os.name").get.toLowerCase.split(" ")(0)
val gpu = Seq(
  "org.bytedeco" % "pytorch-platform-gpu" % "2.10.0-1.5.13" % Provided,
  "org.bytedeco" % "cuda" % "13.1-9.19-1.5.13" % Provided classifier s"$os-x86_64"
)

libraryDependencies ++= (
  os match {
    case "linux" | "windows" => gpu
    case _ => Seq.empty
  }
)
