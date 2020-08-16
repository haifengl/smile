name := "smile-io"

libraryDependencies ++= {
  val arrowV = "0.16.0"
  Seq(
    "org.apache.arrow" % "arrow-memory" % arrowV % Provided,
    "org.apache.arrow" % "arrow-vector" % arrowV % Provided,
    // parquet-jackson includes jackson
    "org.apache.parquet" % "parquet-hadoop" % "1.10.1" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    // required by parquet
    "org.apache.hadoop" % "hadoop-common" % "3.0.0" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    "org.apache.avro" % "avro" % "1.8.2" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    // SAS7BDAT
    "com.epam" % "parso" % "2.0.11",
    "org.apache.commons" % "commons-csv" % "1.8",
    "org.xerial" % "sqlite-jdbc" % "3.32.3.2" % Test
  )
}
