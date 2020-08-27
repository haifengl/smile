name := "smile-math"

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "org.bytedeco" % "javacpp"            % "1.5.3"       % "provided",
  "org.bytedeco" % "openblas"           % "0.3.9-1.5.3" % "provided",
  "org.bytedeco" % "openblas-platform"  % "0.3.9-1.5.3" % "provided",
  "org.bytedeco" % "arpack-ng"          % "3.7.0-1.5.3" % "provided",
  "org.bytedeco" % "arpack-ng-platform" % "3.7.0-1.5.3" % "provided"
)
