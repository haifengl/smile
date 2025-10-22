import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("buildlogic.common-conventions")
    kotlin("jvm")
    // Generates HTML documentation
    id("org.jetbrains.dokka")
}

dependencies {
    implementation(kotlin("stdlib"))
}

// Compile bytecode to Java 25
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

// Configure existing Dokka task to output HTML
dokka {
    pluginsConfiguration.html {
        footerMessage.set("Copyright © 2010-2025 Haifeng Li. All rights reserved. Use is subject to license terms.")
    }
}

