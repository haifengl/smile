name := "smile-plot"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.plot")

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-xml" % "2.18.3",
  "org.swinglabs" % "swingx" % "1.6.1"
)
