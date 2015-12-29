name := "smile-scala"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true

autoScalaLibrary := true

scalacOptions in (Compile,doc) ++= Seq("-groups", "-implicits")

scalacOptions in (Compile, doc) ++= Opts.doc.title("SMILE &mdash; Statistical Machine Intelligence and Learning Engine")

libraryDependencies += "com.thoughtworks.xstream" % "xstream" % "1.4.8"
