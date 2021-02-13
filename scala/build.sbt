name := "smile-scala"

scalacOptions in (Compile, doc) ++= Seq(
  "-doc-root-content", baseDirectory.value + "/root-doc.txt",
  "-doc-title", "Smile - Statistical Machine Intelligence and Learning Engine"
)
target in Compile in doc := baseDirectory.value / "../doc/api/scala"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0",
  "com.thoughtworks.xstream" % "xstream" % "1.4.15",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "org.specs2" %% "specs2-core" % "4.10.6" % Test
)
