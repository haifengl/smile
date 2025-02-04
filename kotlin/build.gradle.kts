import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "2.1.10"
    id("buildlogic.common-conventions")
    id("buildlogic.maven-publish-conventions")
    // Generates HTML documentation
    id("org.jetbrains.dokka") version "2.0.0"
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":core"))
    api(project(":nlp"))
}

// Compile bytecode to Java 21
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
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
            remoteUrl("https://github.com/haifengl/smile/tree/master/kotlin/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration.html {
        footerMessage.set("Copyright © 2010-2024 Haifeng Li. All rights reserved. Use is subject to license terms.")
    }
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("../../doc/api/kotlin"))
    }
}

// Build javadoc.jar from dokka task output
/*
tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaGenerate)
    from(tasks.dokkaGenerate.flatMap { it.outputDirectory })
    archiveClassifier.set("html-docs")
}
*/
signing {
    // useGpgCmd()
}
