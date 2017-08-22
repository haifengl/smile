name := "smile-netlib"

libraryDependencies ++= Seq(
  "com.github.fommil.netlib" % "all"                  % "1.1.2" pomOnly(),
  "net.sourceforge.f2j"      % "arpack_combined_all"  % "0.1"
)