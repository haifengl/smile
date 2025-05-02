plugins {
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("buildlogic.common-conventions")
    // Apply the scala Plugin to add support for Scala.
    scala
}

dependencies {
    implementation("org.scala-lang:scala3-library_3:3.3.5")
    implementation("com.typesafe.scala-logging:scala-logging_3:3.9.5")

    // Use Specs2 for testing.
    testImplementation("org.specs2:specs2-core_3:4.21.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.withType<ScalaCompile> {
    options.compilerArgs.add("-release:21")
    options.compilerArgs.add("-encoding:utf8")
    options.compilerArgs.add("-feature")
    options.compilerArgs.add("-deprecation")
    options.compilerArgs.add("-unchecked")
}
