name := "smile-io"

libraryDependencies ++= {
  val version = "0.11.0"
  Seq(
    "org.apache.arrow" % "arrow-memory" % version,
    "org.apache.arrow" % "arrow-vector" % version
  )
}

libraryDependencies += "org.apache.parquet" % "parquet-hadoop" % "1.10.0"

libraryDependencies += "org.apache.hadoop" % "hadoop-common" % "3.0.3"

libraryDependencies += "org.apache.commons" % "commons-csv" % "1.6"

libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.25.2" % Test