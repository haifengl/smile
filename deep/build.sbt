name := "smile-deep"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.deep")

libraryDependencies ++= Seq(
  "org.slf4j"      % "slf4j-api"            % "1.7.30",
  "org.bytedeco"   % "pytorch"              % "2.0.1-1.5.9" excludeAll(
    ExclusionRule(organization = "org.bytedeco", name = "javacpp-platform"),
    ExclusionRule(organization = "org.bytedeco", name = "openblas-platform")
  ),
  "org.bytedeco"   % "pytorch-platform-gpu" % "2.0.1-1.5.9" excludeAll(
    ExclusionRule(organization = "org.bytedeco", name = "javacpp-platform"),
    ExclusionRule(organization = "org.bytedeco", name = "openblas-platform")
  ),
  "org.bytedeco"   % "cuda-platform-redist" % "12.1-8.9-1.5.9" excludeAll(
    ExclusionRule(organization = "org.bytedeco", name = "javacpp-platform"),
    ExclusionRule(organization = "org.bytedeco", name = "openblas-platform")
  ),
  "org.bytedeco" % "javacpp"  % "1.5.9"        classifier "macosx-x86_64" classifier "macosx-arm64" classifier "windows-x86_64" classifier "linux-x86_64",
  "org.bytedeco" % "openblas" % "0.3.23-1.5.9" classifier "macosx-x86_64" classifier "macosx-arm64" classifier "windows-x86_64" classifier "linux-x86_64"
)
