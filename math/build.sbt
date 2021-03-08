name := "smile-math"

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "org.bytedeco" % "javacpp"   % "1.5.5",
  "org.bytedeco" % "openblas"  % "0.3.13-1.5.5",
  "org.bytedeco" % "arpack-ng" % "3.8.0-1.5.5",
  "org.bytedeco" % "javacpp"   % "1.5.5"        % "provided" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
  "org.bytedeco" % "openblas"  % "0.3.13-1.5.5" % "provided" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
  "org.bytedeco" % "arpack-ng" % "3.8.0-1.5.5"  % "provided" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64"
)
