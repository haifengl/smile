name := "smile-spark"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.spark")

libraryDependencies ++= {
  val sparkV = "4.1.2"
  Seq(
    "org.apache.spark" %% "spark-core"  % sparkV % Provided,
    "org.apache.spark" %% "spark-sql"   % sparkV % Provided,
    "org.apache.spark" %% "spark-mllib" % sparkV % Provided,
    "org.apache.hadoop" % "hadoop-common" % "3.5.0" % Provided
  )
}
