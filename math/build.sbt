name := "smile-math"

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "org.bytedeco" % "javacpp"   % "1.5.12",
  "org.bytedeco" % "openblas"  % "0.3.30-1.5.12",
  "org.bytedeco" % "arpack-ng" % "3.9.1-1.5.12",
  "org.bytedeco" % "javacpp"   % "1.5.12"        % "provided" classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
  "org.bytedeco" % "openblas"  % "0.3.30-1.5.12" % "provided" classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
  "org.bytedeco" % "arpack-ng" % "3.9.1-1.5.12"  % "provided" classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64"
)
