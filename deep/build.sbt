name := "smile-deep"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.deep")

libraryDependencies ++= Seq(
  "org.slf4j"      % "slf4j-api" % "1.7.30",
  "org.bytedeco"   % "pytorch" % "2.0.1-1.5.9",
  "org.bytedeco"   % "pytorch-platform-gpu" % "2.0.1-1.5.9",
  "org.bytedeco"   % "cuda-platform-redist" % "12.1-8.9-1.5.9",
  "org.bytedeco"   % "mkl-platform-redist"  % "2023.1-1.5.9"
)
