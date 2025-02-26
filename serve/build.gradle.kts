plugins {
    id("buildlogic.scala-application-conventions")
}

dependencies {
    implementation(project(":deep"))
    implementation(libs.bundles.akka)
    implementation(libs.logback)
    implementation(libs.sqlite)
    implementation("com.github.scopt:scopt_3:4.1.0")
    implementation("com.typesafe.slick:slick_3:3.5.2")
}

application {
    // Define the main class for the application.
    mainClass = "smile.serve.Main"
}
