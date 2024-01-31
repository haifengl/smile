name := "smile-base"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.base")

libraryDependencies ++= {
  val arrowV = "15.0.0"
  Seq(
    "org.bytedeco" % "javacpp"   % "1.5.10"        % "provided" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "openblas"  % "0.3.26-1.5.10" % "provided" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "arpack-ng" % "3.9.1-1.5.10"  % "provided" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64" classifier "",
    "org.apache.arrow" % "arrow-vector" % arrowV % Provided,
    "org.apache.arrow" % "arrow-memory" % arrowV % Provided,
    "org.apache.arrow" % "arrow-memory-netty" % arrowV % Provided,
    "org.apache.parquet" % "parquet-hadoop" % "1.13.1" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    "org.apache.hadoop" % "hadoop-common" % "3.3.6" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    "org.apache.avro" % "avro" % "1.11.3" % Provided exclude("org.slf4j", "slf4j-log4j12"),
    "com.epam" % "parso" % "2.0.14", // SAS7BDAT
    "org.apache.commons" % "commons-csv" % "1.10.0",
    "org.xerial" % "sqlite-jdbc" % "3.45.1.0" % Test
  )
}
