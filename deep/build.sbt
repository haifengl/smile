name := "smile-deep"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.deep")

libraryDependencies ++= Seq(
  "org.bytedeco"   % "pytorch-platform"     % "2.1.2-1.5.10" excludeAll(
    ExclusionRule(organization = "org.bytedeco", name = "javacpp-platform"),
    ExclusionRule(organization = "org.bytedeco", name = "openblas-platform")
  ),
  "org.bytedeco"   % "pytorch-platform-gpu" % "2.1.2-1.5.10" excludeAll(
    ExclusionRule(organization = "org.bytedeco", name = "javacpp-platform"),
    ExclusionRule(organization = "org.bytedeco", name = "openblas-platform")
  ),
  "org.bytedeco"   % "cuda-platform-redist" % "12.3-8.9-1.5.10" excludeAll(
    ExclusionRule(organization = "org.bytedeco", name = "javacpp-platform"),
    ExclusionRule(organization = "org.bytedeco", name = "openblas-platform")
  )
)
