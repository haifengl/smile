name := "smile-spark"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.spark")

libraryDependencies ++= {
  val sparkV = "3.4.1"
  Seq(
    "org.apache.spark" %% "spark-core"  % sparkV % Provided,
    "org.apache.spark" %% "spark-sql"   % sparkV % Provided,
    "org.apache.spark" %% "spark-mllib" % sparkV % Provided,
    "org.bytedeco" % "javacpp"   % "1.5.10"        % "test" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "openblas"  % "0.3.26-1.5.10" % "test" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "arpack-ng" % "3.9.1-1.5.10"  % "test" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64"
  )
}
