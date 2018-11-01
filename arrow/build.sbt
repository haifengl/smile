name := "smile-arrow"

libraryDependencies ++= {
  val version = "0.11.0"
  Seq(
    "org.apache.arrow" % "arrow-memory" % version,
    "org.apache.arrow" % "arrow-vector" % version
  )
}

