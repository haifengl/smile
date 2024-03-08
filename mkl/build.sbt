name := "smile-mkl"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.mkl")

libraryDependencies ++= {
  val version = "2024.0-1.5.10"
  Seq(
    "org.bytedeco" % "mkl-platform"        % version,
    "org.bytedeco" % "mkl-platform-redist" % version
  )
}
