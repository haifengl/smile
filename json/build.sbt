name := "smile-json"

scalacOptions in (Compile, doc) ++= Seq(
  "-doc-root-content", baseDirectory.value + "/root-doc.txt",
  "-doc-title", "Smile - JSON"
)
target in Compile in doc := baseDirectory.value / "../doc/api/json"

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "org.specs2" %% "specs2-core" % "4.10.6" % "test"
)
