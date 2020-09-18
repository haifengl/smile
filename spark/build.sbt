name := "smile-spark"

// Parent project disables Scala as most libraries are in Java.
// Enable it as this is a Scala project.
crossPaths := true
autoScalaLibrary := true

libraryDependencies ++= {
  val sparkV = "3.0.1"
  Seq(
    "org.apache.spark" %% "spark-core" % sparkV % Provided,
    "org.apache.spark" %% "spark-sql"  % sparkV % Provided
  )
}
