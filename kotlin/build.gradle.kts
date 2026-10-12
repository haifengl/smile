plugins {
    id("buildlogic.kotlin-library-conventions")
}

dependencies {
    api(project(":core"))
    api(project(":nlp"))
    api(project(":plot"))

    // The Kotlin scripting host API used by smile.studio.kernel.ScriptRunnerBridge.
    // It is only needed by hosts that embed the Kotlin scripting engine (Studio),
    // not by consumers of the smile-kotlin API, so it is kept off the published
    // POM (compileOnly). A host must put the same artifacts on its runtime
    // classpath.
    compileOnly(libs.kotlin.scripting.jvm.host)
    compileOnly(libs.kotlin.scripting.common)
    compileOnly(libs.kotlin.scripting.jvm)
    compileOnly(libs.kotlin.compiler.embeddable)
}

// ReadTest exercises the optional file formats of smile-base (Arrow/Parquet, Avro, SAS), which base declares
// compile-only; a consumer that reads those formats provides the libraries itself.
dependencies {
    testRuntimeOnly(libs.bundles.arrow)
    testRuntimeOnly("com.epam:parso:2.0.14") // SAS7BDAT
    testRuntimeOnly("org.apache.avro:avro:1.12.2") { exclude("org.slf4j", "slf4j-log4j12") }
    testRuntimeOnly("org.xerial.snappy:snappy-java:1.1.10.12") // for avro
}

// The Kotlin scripting host API is not needed at test time either, as the
// Kotlin module tests exercise the SMILE API rather than the scripting bridge.

// Sets test working directory to parent (root) directory
tasks.withType<Test> {
    workingDir = rootProject.projectDir
}

// Configure existing Dokka task to output HTML
dokka {
    moduleName.set("smile-kotlin")
    dokkaSourceSets.main {
        includes.from("packages.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/haifengl/smile/tree/master/kotlin/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("../../target/docs/kotlin"))
    }
}
