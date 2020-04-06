name := "smile-scala"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true

autoScalaLibrary := true

scalacOptions in (Compile, doc) ++= Seq("-groups", "-implicits")

scalacOptions in (Compile, doc) ++= Seq("-doc-root-content", baseDirectory.value + "/root-doc.txt")

scalacOptions in (Compile, doc) ++= Seq("-doc-title", "Smile - Statistical Machine Intelligence and Learning Engine")

target in Compile in doc := baseDirectory.value / "../docs/2.0/api/scala"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.3.0"

libraryDependencies += "com.thoughtworks.xstream" % "xstream" % "1.4.11.1"

// read.avro() needs org.apache.avro.Schema defintion
libraryDependencies += "org.apache.avro" % "avro" % "1.8.2" % Provided exclude("org.slf4j", "slf4j-log4j12")

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

libraryDependencies += "org.specs2" %% "specs2-core" % "4.9.2" % "test",
