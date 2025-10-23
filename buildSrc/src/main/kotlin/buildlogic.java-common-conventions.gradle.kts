plugins {
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("buildlogic.common-conventions")
    // Apply the java Plugin to add support for Java.
    java
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.bytedeco.gradle-javacpp-build:org.bytedeco.gradle-javacpp-build.gradle.plugin:1.5.10")

    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

tasks.withType<JavaCompile> {
    // Set the source and target compatibility to Java 21
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()

    options.encoding = "UTF-8"
    options.compilerArgs.add("-g:lines,vars,source")
    options.compilerArgs.add("-Xlint:deprecation")
    options.compilerArgs.add("-Xlint:unchecked")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    workingDir = project.rootDir
}
