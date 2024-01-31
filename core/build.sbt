name := "smile-core"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.core")

libraryDependencies ++= Seq(
  "org.bytedeco" % "javacpp"   % "1.5.10"        % "provided" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
  "org.bytedeco" % "openblas"  % "0.3.26-1.5.10" % "provided" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
  "org.bytedeco" % "arpack-ng" % "3.9.1-1.5.10"  % "provided" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64" classifier ""
)
