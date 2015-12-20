name := "smile-benchmark"

crossPaths := true

autoScalaLibrary := true

enablePlugins(JavaAppPackaging)

maintainer := "Haifeng Li <haifeng.hli@gmail.com>"

packageSummary := "SMILE Benchmark"

packageDescription := "Benchmark of SMILE machine learning library."

mainClass in Compile := Some("smile.benchmark.Benchmark")

