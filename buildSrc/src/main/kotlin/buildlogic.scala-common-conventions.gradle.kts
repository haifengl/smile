plugins {
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("buildlogic.common-conventions")
    // Apply the scala Plugin to add support for Scala.
    scala
}

dependencies {
    implementation("org.scala-lang:scala3-library_3:3.3.7")
    implementation("com.typesafe.scala-logging:scala-logging_3:3.9.6")

    // Use ScalaTest for testing.
    testImplementation("org.scalatest:scalatest_3:3.2.20")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.18")
}

tasks.withType<ScalaCompile> {
    options.compilerArgs.add("-release:25")
    options.compilerArgs.add("-encoding:utf8")
    options.compilerArgs.add("-feature")
    options.compilerArgs.add("-deprecation")
    options.compilerArgs.add("-unchecked")
}
