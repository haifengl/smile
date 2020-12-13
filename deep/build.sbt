name := "smile-deep"

libraryDependencies ++= {
  val tensorV = "0.2.0"
  Seq(
    "org.slf4j"      % "slf4j-api" % "1.7.30",
    "org.tensorflow" % "tensorflow-core-platform" % tensorV % "provided"
    // or support for MKL and GPU on Linux and Windows
    //"org.tensorflow" % "tensorflow-core-platform-mkl-gpu" % tensorV,
  )
}
