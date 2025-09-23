plugins {
    id("buildlogic.scala-application-conventions")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":core"))
    implementation(project(":nlp"))
    implementation(project(":scala"))
    implementation(libs.logback)
    implementation(libs.bundles.akka)
    implementation("org.scala-lang:scala3-compiler_3:3.3.5")
    implementation("com.lightbend.akka:akka-stream-alpakka-csv_3:8.0.0")
    implementation("com.github.scopt:scopt_3:4.1.0")
    implementation("com.formdev:flatlaf:3.6.1")
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    implementation("com.fifesoft:autocomplete:3.3.2")
}

application {
    // Define the main class for the application.
    mainClass = "smile.shell.Main"
}
