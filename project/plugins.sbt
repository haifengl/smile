resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.17.0")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.0")
addSbtPlugin("com.github.sbt" % "sbt-eclipse" % "6.2.0")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")
addSbtPlugin("org.jetbrains.scala" % "sbt-kotlin-plugin" % "3.1.7")
addDependencyTreePlugin
