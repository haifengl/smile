name := "smile-mkl"

libraryDependencies ++= {
  val version = "2022.2-1.5.8"
  Seq(
    "org.bytedeco" % "mkl-platform"        % version,
    "org.bytedeco" % "mkl-platform-redist" % version,
    "org.bytedeco" % "openblas"  % "0.3.21-1.5.8" % "test" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
    "org.bytedeco" % "arpack-ng" % "3.8.0-1.5.8"  % "test" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64"
  )
}
