import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Compile bytecode to Java 21
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

plugins {
    kotlin("jvm") version "2.1.10"
    id("buildlogic.common-conventions")
    id("buildlogic.maven-publish-conventions")
    // Generates HTML documentation
    id("org.jetbrains.dokka") version "2.0.0"
    // Generates Javadoc documentation
    id("org.jetbrains.dokka-javadoc") version "2.0.0"
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":core"))
    api(project(":nlp"))
}

// Copy jar to shell lib
val copyJarToShell by tasks.registering(Copy::class) {
    from(tasks.jar)
    into(file("../shell/src/universal/bin"))
}

// Run copy task after build
tasks.build {
    finalizedBy(copyJarToShell)
}

// Configure existing Dokka task to output HTML
dokka {
    moduleName.set("Smile Kotlin")
    dokkaSourceSets.main {
        includes.from("packages.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/haifengl/smile")
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration.html {
        footerMessage.set("Copyright © 2010-2024 Haifeng Li. All rights reserved. Use is subject to license terms.")
    }
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("../doc/api/kotlin"))
    }
}

// Create dokka Jar task from dokka task output
tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("html-docs")
}

tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

signing {
    useGpgCmd()
}
