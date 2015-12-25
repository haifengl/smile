name := "smile-dsel"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true

autoScalaLibrary := true

libraryDependencies += "com.thoughtworks.xstream" % "xstream" % "1.4.8"