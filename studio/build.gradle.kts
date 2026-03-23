plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":core"))
    implementation(project(":nlp"))
    implementation(project(":plot"))
    implementation(libs.swingx)
    implementation("org.scala-lang:scala3-compiler_3:3.3.7")
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("com.openai:openai-java:4.16.1")
    implementation("com.anthropic:anthropic-java:2.11.1")
    implementation("com.google.genai:google-genai:1.36.0")
    implementation("org.commonmark:commonmark:0.27.1")
    implementation("org.xhtmlrenderer:flying-saucer-core:10.0.6")
    implementation("com.fifesoft:rsyntaxtextarea:3.6.1")
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-fonts-jetbrains-mono:2.304")
    implementation("org.apache.maven:maven-resolver-provider:3.9.12")
    implementation("org.apache.maven.resolver:maven-resolver-supplier-mvn4:2.0.14")
}

application {
    // Define the main class for the application.
    mainClass = "smile.Main"
}

tasks.withType<Javadoc> {
    enabled = false
}
