import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// compile bytecode to java 8 (default is java 6)
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

plugins {
    `maven-publish`
    kotlin("jvm") version "1.3.70" 
    id("org.jetbrains.dokka") version "0.10.0"
}

group = "com.github.haifengl"
version = "2.3.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib")) 
    implementation("com.github.haifengl:smile-core:2.2.2")
    implementation("com.github.haifengl:smile-io:2.2.2")
}

// Configure existing Dokka task to output HTML
tasks.dokka {
    outputFormat = "html"
    outputDirectory = "../docs/2.0/api/kotlin"
}

// Create dokka Jar task from dokka task output
val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    classifier = "javadoc"
    // dependsOn(tasks.dokka) not needed; dependency automatically inferred by from(tasks.dokka)
    from(tasks.dokka)
}

// Create sources Jar from main kotlin sources
val sourcesJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles sources JAR"
    classifier = "sources"
    from(project.the<SourceSetContainer>()["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("default") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(dokkaJar)
        }
    }
    repositories {
        maven {
            url = uri("$buildDir/repository")
        }
    }
}
