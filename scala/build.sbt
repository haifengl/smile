name := "smile-scala"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.scala")

Compile / doc / scalacOptions ++= Seq(
  "-project", "Smile",
  "-doc-root-content", baseDirectory.value + "/root-doc.txt"
)
Compile / doc / target := baseDirectory.value / "../doc/api/scala"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "2.3.0"
)
