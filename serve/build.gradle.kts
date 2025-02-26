plugins {
    id("buildlogic.scala-application-conventions")
}

dependencies {
    implementation(project(":deep"))
    implementation(libs.bundles.akka)
    implementation(libs.sqlite)
    implementation("com.github.scopt:scopt_3:4.1.0")
    implementation("com.typesafe.slick:slick_3:3.5.2")
    implementation("ch.qos.logback:logback-classic:1.5.16")
}

application {
    // Define the main class for the application.
    mainClass = "smile.serve.Main"
}
