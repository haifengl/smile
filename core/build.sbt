name := "smile-core"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.core")

libraryDependencies ++= {
  val version = "2024.0-1.5.10"
  Seq(
    "org.bytedeco" % "mkl-platform"        % version % "test",
    "org.bytedeco" % "mkl-platform-redist" % version % "test"
  )
}
