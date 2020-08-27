name := "smile-core"

libraryDependencies ++= Seq(
  "org.bytedeco" % "javacpp"            % "1.5.3"       % "test",
  "org.bytedeco" % "openblas"           % "0.3.9-1.5.3" % "test",
  "org.bytedeco" % "openblas-platform"  % "0.3.9-1.5.3" % "test",
  "org.bytedeco" % "arpack-ng"          % "3.7.0-1.5.3" % "test",
  "org.bytedeco" % "arpack-ng-platform" % "3.7.0-1.5.3" % "test"
)

