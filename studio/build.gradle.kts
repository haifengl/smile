plugins {
    id("buildlogic.scala-application-conventions")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":core"))
    implementation(project(":nlp"))
    implementation(project(":scala"))
    implementation("org.scala-lang:scala3-compiler_3:3.3.7")
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.fifesoft:rsyntaxtextarea:3.6.1")
}

application {
    // Define the main class for the application.
    mainClass = "smile.Main"
}
