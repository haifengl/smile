name := "smile-base"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.base")

libraryDependencies ++= {
  val arrowV = "18.3.0"
  Seq(
    "org.apache.arrow" % "arrow-dataset" % arrowV % Provided,
    "org.apache.arrow" % "arrow-memory-netty" % arrowV % Provided,
    "org.apache.avro" % "avro" % "1.12.1" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    "org.xerial.snappy" % "snappy-java" % "1.1.10.8", // for avro
    "com.epam" % "parso" % "2.0.14" % Provided, // SAS7BDAT
    "org.apache.commons" % "commons-csv" % "1.14.1",
    "org.duckdb" % "duckdb_jdbc" % "1.4.3.0",
    "org.xerial" % "sqlite-jdbc" % "3.51.1.0" % Test
  )
}
