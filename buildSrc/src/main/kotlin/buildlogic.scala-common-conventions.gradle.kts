plugins {
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("buildlogic.common-conventions")
    // Apply the scala Plugin to add support for Scala.
    scala
}

dependencies {
    val scalaBinVersion = "2.13"
    constraints {
        implementation("org.scala-lang:scala-library:2.13.16")
    }

    implementation("org.scala-lang:scala-library")
    implementation("com.typesafe.scala-logging:scala-logging_$scalaBinVersion:3.9.5")

    // Use Specs2 for testing.
    testImplementation("org.specs2:specs2-core_$scalaBinVersion:4.20.9")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}
