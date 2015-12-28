name := "smile-benchmark"

crossPaths := true

autoScalaLibrary := true

// Don't publish to central Maven repo
publishArtifact := false

mainClass in Compile := Some("smile.benchmark.Benchmark")