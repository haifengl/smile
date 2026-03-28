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
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Compile bytecode to Java 25
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

// JUnit5
tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

// Configure existing Dokka task to output HTML
dokka {
    pluginsConfiguration.html {
        footerMessage.set("Copyright © 2010-2026 Haifeng Li. All rights reserved. Use is subject to license terms.")
    }
}

