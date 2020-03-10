name := "smile-nd4j"

libraryDependencies ++= {
  val nd4jV = "1.0.0-beta6"
  Seq(
    "org.nd4j"   %  "nd4j-native-platform"    % nd4jV
    //"org.nd4j"   %  "nd4j-cuda-7.5-platform"  % nd4jV
  )
}
