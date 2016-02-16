name := "smile-benchmark"

crossPaths := true

autoScalaLibrary := true

mainClass in Compile := Some("smile.benchmark.Benchmark")

// Don't publish to central Maven repo
publishArtifact := false