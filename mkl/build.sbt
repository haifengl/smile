name := "smile-mkl"

libraryDependencies ++= {
  val version = "2020.1-1.5.3"
  Seq(
    "org.bytedeco" % "mkl-platform"        % version exclude("org.bytedeco", "javacpp"),
    "org.bytedeco" % "mkl-platform-redist" % version exclude("org.bytedeco", "javacpp")
  )
}
