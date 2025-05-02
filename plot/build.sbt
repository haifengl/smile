name := "smile-plot"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.plot")

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-xml" % "2.19.0",
  "org.swinglabs" % "swingx" % "1.6.1"
)
