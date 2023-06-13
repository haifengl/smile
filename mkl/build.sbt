name := "smile-mkl"

libraryDependencies ++= {
  val version = "2023.1-1.5.9"
  Seq(
    "org.bytedeco" % "mkl-platform"        % version,
    "org.bytedeco" % "mkl-platform-redist" % version,
    "org.bytedeco" % "openblas"  % "0.3.23-1.5.9" % "test" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "arpack-ng" % "3.9.0-1.5.9"  % "test" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64"
  )
}
