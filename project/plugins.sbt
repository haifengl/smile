resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")

addSbtPlugin("net.aichler" % "sbt-jupiter-interface" % "0.11.1")

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")

addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

addSbtPlugin("com.github.sbt" % "sbt-eclipse" % "6.2.0")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")

addDependencyTreePlugin
