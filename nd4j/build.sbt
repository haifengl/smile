name := "smile-nd4j"

libraryDependencies ++= {
  val nd4jV = "0.8.0"
  Seq(
    "org.nd4j"   %  "nd4j-native-platform"    % nd4jV
    //"org.nd4j"   %  "nd4j-cuda-7.5-platform"  % nd4jV
  )
}