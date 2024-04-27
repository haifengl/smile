name := "smile-plot"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.plot")

libraryDependencies ++= Seq(
  "org.swinglabs" % "swingx" % "1.6.1",
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-xml" % "2.17.0"
)
