plugins {
    id("buildlogic.kotlin-library-conventions")
}

dependencies {
    api(project(":core"))
    api(project(":nlp"))

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
        outputDirectory.set(layout.buildDirectory.dir("../../doc/kotlin"))
    }
}
