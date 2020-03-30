name := "smile-scala"

crossScalaVersions := Seq("2.11.12", "2.12.11", "2.13.1")

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true

autoScalaLibrary := true

scalacOptions in (Compile, doc) ++= Seq("-groups", "-implicits")

scalacOptions in (Compile, doc) ++= Seq("-doc-root-content", baseDirectory.value + "/root-doc.txt")

scalacOptions in (Compile, doc) ++= Seq("-doc-title", "Smile - Statistical Machine Intelligence and Learning Engine")

target in Compile in doc := baseDirectory.value / "../docs/2.0/api/scala"

libraryDependencies += "com.thoughtworks.xstream" % "xstream" % "1.4.11.1"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
