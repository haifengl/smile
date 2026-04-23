plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":core"))
    implementation(project(":nlp"))
    implementation(project(":plot"))
    implementation(libs.swingx)
    implementation(libs.bundles.jackson)
    implementation("org.scala-lang:scala3-compiler_3:3.3.7")
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("com.openai:openai-java:4.31.0")
    implementation("com.anthropic:anthropic-java:2.24.0")
    implementation("com.google.genai:google-genai:1.47.0")
    implementation("org.commonmark:commonmark:0.27.1")
    implementation("org.xhtmlrenderer:flying-saucer-core:10.2.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")
    implementation("com.fifesoft:rsyntaxtextarea:3.6.2")
    implementation("com.fifesoft:rstaui:3.3.2")
    implementation("com.fifesoft:spellchecker:3.4.1")
    implementation("com.formdev:flatlaf:3.7.1")
    implementation("com.formdev:flatlaf-fonts-jetbrains-mono:2.304")
    implementation("org.apache.maven:maven-resolver-provider:3.9.15")
    implementation("org.apache.maven.resolver:maven-resolver-supplier-mvn4:2.0.16")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.1.2")
    implementation("io.modelcontextprotocol.sdk:mcp:1.1.1")
    implementation("io.github.furstenheim:copy_down:1.1")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.github.serpapi:serpapi-java:1.1.0")
    implementation("com.google.code.gson:gson:2.13.2") // evict older version used by serpapi
    // local packages
    implementation(fileTree("lib/") { include("*.jar") })
}

application {
    // Define the main class for the application.
    mainClass = "smile.Main"
}

distributions {
    main {
        contents {
            from("src/universal")
        }
    }
}

tasks.withType<Javadoc> {
    enabled = false
}
