name := "smile-deep"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.deep")

libraryDependencies ++= Seq(
  "ai.djl.sentencepiece" % "sentencepiece"  % "0.28.0",
  "org.bytedeco"   % "pytorch-platform"     % "2.2.2-1.5.11-SNAPSHOT",
  "org.bytedeco"   % "pytorch-platform-gpu" % "2.2.2-1.5.11-SNAPSHOT" % Provided,
  "org.bytedeco"   % "cuda" % "12.3-8.9-1.5.11-SNAPSHOT" % Provided classifier "windows-x86_64-redist"
)
