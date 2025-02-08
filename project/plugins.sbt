resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.4")
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.13.3")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.0")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.github.sbt" % "sbt-eclipse" % "6.2.0")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")
addDependencyTreePlugin
