name := "smile-demo"

enablePlugins(JavaAppPackaging)

maintainer := "Haifeng Li <haifeng.hli@gmail.com>"

packageSummary := "SMILE Demo"

packageDescription := "Demo of SMILE machine learning library."

mainClass in Compile := Some("smile.demo.SmileDemo")

