name := "smile-spark"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.spark")

libraryDependencies ++= {
  val sparkV = "4.2.0"
  val scalaV = "2.13"
  Seq(
    "org.apache.spark"  % s"spark-core_$scalaV"  % sparkV  % Provided,
    "org.apache.spark"  % s"spark-sql_$scalaV"   % sparkV  % Provided,
    "org.apache.spark"  % s"spark-mllib_$scalaV" % sparkV  % Provided,
    "org.apache.hadoop" % "hadoop-common"        % "3.5.0" % Provided
  )
}
