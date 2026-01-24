name := "smile-plot"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.plot")

libraryDependencies ++= Seq(
  "tools.jackson.core" % "jackson-databind" % "3.0.4",
  "org.swinglabs" % "swingx" % "1.6.1"
)
