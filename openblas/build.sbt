name := "smile-openblas"

libraryDependencies ++= {
  val version = "0.3.9-1.5.3"
  Seq(
    "org.bytedeco" % "openblas"          % version,
    "org.bytedeco" % "openblas-platform" % version
  )
}
