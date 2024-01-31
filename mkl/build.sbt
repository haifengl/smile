name := "smile-mkl"

libraryDependencies ++= {
  val version = "2024.0-1.5.10"
  Seq(
    "org.bytedeco" % "mkl-platform"        % version,
    "org.bytedeco" % "mkl-platform-redist" % version,
    "org.bytedeco" % "openblas"  % "0.3.26-1.5.10" % "test" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "arpack-ng" % "3.9.1-1.5.10"  % "test" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64" classifier ""
  )
}
