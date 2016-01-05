name := "smile-scala"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true

autoScalaLibrary := true

scalacOptions in (Compile,doc) ++= Seq("-diagrams", "-groups", "-implicits")

scalacOptions in (Compile, doc) ++= Opts.doc.title("SMILE - Statistical Machine Intelligence and Learning Engine")

target in Compile in doc := baseDirectory.value / "../shell/src/universal/doc/api/scala"

libraryDependencies += "com.thoughtworks.xstream" % "xstream" % "1.4.8"
