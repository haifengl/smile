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
    `maven-publish`
    kotlin("jvm") version "2.1.10"
    id("buildlogic.common-conventions")
    // Generates HTML documentation
    id("org.jetbrains.dokka") version "2.0.0"
    // Generates Javadoc documentation
    id("org.jetbrains.dokka-javadoc") version "2.0.0"
    signing
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.github.haifengl"
            artifactId = "smile-kotlin"
            from(components["java"])
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set("smile-kotlin")
                description.set("Statistical Machine Intelligence and Learning Engine")
                url.set("https://haifengl.github.io//")
                licenses {
                    license {
                        name.set("GNU General Public License, Version 3")
                        url.set("https://opensource.org/licenses/GPL-3.0")
                    }
                }
                developers {
                    developer {
                        id.set("haifengl")
                        name.set("Haifeng Li")
                        url.set("https://haifengl.github.io/")
                    }
                }
                scm {
                    connection.set("git@github.com:haifengl/smile.git")
                    developerConnection.set("scm:git:git@github.com:haifengl/smile.git")
                    url.set("https://github.com/haifengl/smile")
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                val nexusUser: String by project
                val nexusPassword: String by project
                username = nexusUser
                password = nexusPassword
            }
        }
    }
}

signing {
    // Conditional signing
    setRequired({
        (project.extra["isReleaseVersion"] as Boolean) && gradle.taskGraph.hasTask("publish")
    })
    useGpgCmd()
    sign(configurations.archives.get())
    sign(publishing.publications["mavenJava"])
}
