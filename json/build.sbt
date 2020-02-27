name := "smile-json"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true

autoScalaLibrary := true

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

libraryDependencies += "org.specs2" %% "specs2-core" % "4.8.3" % "test",
