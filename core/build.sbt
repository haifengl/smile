name := "smile-core"

libraryDependencies ++= Seq(
  "org.bytedeco" % "javacpp"            % "1.5.4"        % "test",
  "org.bytedeco" % "openblas"           % "0.3.10-1.5.4" % "test",
  "org.bytedeco" % "openblas-platform"  % "0.3.10-1.5.4" % "test",
  "org.bytedeco" % "arpack-ng"          % "3.7.0-1.5.4"  % "test",
  "org.bytedeco" % "arpack-ng-platform" % "3.7.0-1.5.4"  % "test"
)

