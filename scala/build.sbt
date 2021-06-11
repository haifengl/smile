name := "smile-scala"

Compile / doc / scalacOptions ++= Seq(
  "-doc-root-content", baseDirectory.value + "/root-doc.txt",
  "-doc-title", "Smile - Statistical Machine Intelligence and Learning Engine"
)
Compile / doc / target := baseDirectory.value / "../doc/api/scala"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "2.0.0",
  "com.thoughtworks.xstream" % "xstream" % "1.4.17"
)
