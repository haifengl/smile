name := "smile-deep"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.deep")

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "org.bytedeco"   % "pytorch-platform"     % "2.2.1-1.5.11-SNAPSHOT" excludeAll(
    ExclusionRule(organization = "org.bytedeco", name = "javacpp-platform"),
    ExclusionRule(organization = "org.bytedeco", name = "openblas-platform")
  ),
  "org.bytedeco"   % "pytorch-platform-gpu" % "2.2.1-1.5.11-SNAPSHOT" excludeAll(
    ExclusionRule(organization = "org.bytedeco", name = "javacpp-platform"),
    ExclusionRule(organization = "org.bytedeco", name = "openblas-platform")
  ),
  "org.bytedeco"   % "cuda-platform-redist" % "12.3-8.9-1.5.11-SNAPSHOT" excludeAll(
    ExclusionRule(organization = "org.bytedeco", name = "javacpp-platform"),
    ExclusionRule(organization = "org.bytedeco", name = "openblas-platform")
  )
)
