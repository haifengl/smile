name := "smile-spark"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true
autoScalaLibrary := true

libraryDependencies ++= {
  val sparkV = "2.4.7"
  Seq(
    "org.apache.spark" %% "spark-core" % sparkV % Provided,
    "org.apache.spark" %% "spark-sql"  % sparkV % Provided,
    "org.apache.spark" %% "spark-mllib"  % sparkV % Provided,
    "org.specs2" %% "specs2-core" % "4.10.3" % Test
  )
}
