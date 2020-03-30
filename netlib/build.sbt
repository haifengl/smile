name := "smile-netlib"

libraryDependencies ++= Seq(
  "com.github.fommil.netlib" % "netlib-native_system-win-x86_64"   % "1.1" classifier "natives",
  "com.github.fommil.netlib" % "netlib-native_system-osx-x86_64"   % "1.1" classifier "natives",
  "com.github.fommil.netlib" % "netlib-native_system-linux-x86_64" % "1.1" classifier "natives",
  "com.github.fommil.netlib" % "netlib-native_system-linux-armhf"  % "1.1" classifier "natives",
  "com.github.fommil.netlib" % "native_system-java"                % "1.1",
  "com.github.fommil.netlib" % "core"                              % "1.1.2",
  "com.github.fommil"        % "jniloader"                         % "1.1",
  "net.sourceforge.f2j"      % "arpack_combined_all"               % "0.1"
)
