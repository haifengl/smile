plugins {
    id("buildlogic.scala-application-conventions")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":core"))
    implementation(project(":nlp"))
    implementation(project(":scala"))
    implementation(libs.bundles.akka)
    implementation("org.scala-lang:scala3-compiler_3:3.3.5")
    implementation("com.lightbend.akka:akka-stream-alpakka-csv_3:8.0.0")
    implementation("com.github.scopt:scopt_3:4.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("com.fifesoft:rsyntaxtextarea:3.5.4")
    implementation("com.fifesoft:autocomplete:3.3.1")
}

application {
    // Define the main class for the application.
    mainClass = "smile.shell.Main"
}
