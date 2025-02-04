plugins {
    id("buildlogic.kotlin-library-conventions")
}

dependencies {
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
        outputDirectory.set(layout.buildDirectory.dir("../../doc/api/kotlin"))
    }
}
