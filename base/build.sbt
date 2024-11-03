name := "smile-base"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.base")

libraryDependencies ++= {
  val arrowV = "17.0.0"
  Seq(
    "org.apache.arrow" % "arrow-vector" % arrowV % Provided,
    "org.apache.arrow" % "arrow-memory" % arrowV % Provided,
    "org.apache.arrow" % "arrow-memory-netty" % arrowV % Provided,
    "org.apache.parquet" % "parquet-hadoop" % "1.13.1" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    "org.apache.hadoop" % "hadoop-common" % "3.4.0" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    "org.apache.avro" % "avro" % "1.11.4" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    "com.epam" % "parso" % "2.0.14", // SAS7BDAT
    "org.apache.commons" % "commons-csv" % "1.12.0",
    "org.duckdb" % "duckdb_jdbc" % "1.1.2",
    "org.xerial" % "sqlite-jdbc" % "3.47.0.0" % Test
  )
}
