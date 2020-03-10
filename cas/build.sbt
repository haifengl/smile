name := "smile-cas"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true

autoScalaLibrary := true

scalacOptions in (Compile, doc) ++= Seq("-groups", "-implicits")

scalacOptions in (Compile, doc) ++= Seq("-doc-root-content", baseDirectory.value + "/root-doc.txt")

scalacOptions in (Compile, doc) ++= Seq("-doc-title", "Smile - Computer Algebra System")

target in Compile in doc := baseDirectory.value / "../docs/2.0/api/cas"

libraryDependencies += "org.specs2" %% "specs2-core" % "4.9.2" % "test",
