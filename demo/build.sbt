name := "smile-demo"

mainClass in Compile := Some("smile.demo.SmileDemo")

// Don't publish to central Maven repo
publishArtifact := false