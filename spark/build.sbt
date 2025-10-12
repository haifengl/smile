name := "smile-spark"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.spark")

libraryDependencies ++= {
  val sparkV = "3.5.6"
  Seq(
    "org.apache.spark" %% "spark-core"  % sparkV % Provided,
    "org.apache.spark" %% "spark-sql"   % sparkV % Provided,
    "org.apache.spark" %% "spark-mllib" % sparkV % Provided
  )
}
