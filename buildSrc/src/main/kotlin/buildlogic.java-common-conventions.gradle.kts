plugins {
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("buildlogic.common-conventions")
    // Apply the java Plugin to add support for Java.
    java
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.bytedeco.gradle-javacpp-build:org.bytedeco.gradle-javacpp-build.gradle.plugin:1.5.10")

    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    workingDir = project.rootDir
}
