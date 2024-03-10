name := "smile-deep"

libraryDependencies ++= Seq(
  "org.slf4j"      % "slf4j-api" % "1.7.30",
  "org.bytedeco"   % "pytorch" % "2.1.2-1.5.10",
  "org.bytedeco"   % "pytorch-platform-gpu" % "2.1.2-1.5.10",
  "org.bytedeco"   % "cuda-platform-redist" % "12.3-8.9-1.5.10",
  "org.bytedeco"   % "mkl-platform-redist"  % "2024.0-1.5.10"
)
