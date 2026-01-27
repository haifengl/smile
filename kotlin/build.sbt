name := "smile-kotlin"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.kotlin")

import kotlin.Keys._
kotlinLib("stdlib")

kotlinVersion := "2.3.0"
kotlincJvmTarget := "25"

libraryDependencies ++= {
  val arrowV = "18.3.0"
  Seq(
    "org.apache.arrow" % "arrow-dataset" % arrowV % Test,
    "org.apache.arrow" % "arrow-memory-netty" % arrowV % Test,
    "org.apache.avro" % "avro" % "1.12.1" % Test exclude("org.slf4j", "slf4j-log4j12"),
    "org.xerial.snappy" % "snappy-java" % "1.1.10.8" % Test, // for avro
    "com.epam" % "parso" % "2.0.14" % Test, // SAS7BDAT
    "org.xerial" % "sqlite-jdbc" % "3.51.1.0" % Test
  )
}
