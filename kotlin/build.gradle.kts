import java.net.URL
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration


// Compile bytecode to Java 21 (default is Java 6)
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

plugins {
    `maven-publish`
    kotlin("jvm") version "1.9.25"
    id("org.jetbrains.dokka") version "1.9.20"
    signing
}

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:1.9.20")
    }
}

group = "com.github.haifengl"
version = "4.2.0"
extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib")) 
    api("com.github.haifengl:smile-core:4.2.0")
    api("com.github.haifengl:smile-nlp:4.2.0")
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
tasks.dokkaHtml {
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        footerMessage = "Copyright © 2010-2024 Haifeng Li. All rights reserved. Use is subject to license terms."
    }
}

tasks {
    dokkaHtml.configure {
        outputDirectory.set(buildDir.resolve("../../doc/api/kotlin"))
        dokkaSourceSets {
            configureEach {
                includes.from("packages.md")
                externalDocumentationLink {
                    url.set(URL("http://haifengl.github.io/api/java/"))
                }
            }
        }
    }
}

// Create dokka Jar task from dokka task output
val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    classifier = "javadoc"
    from(tasks.dokkaHtml)
}

// Create sources Jar from main kotlin sources
val sourcesJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles sources JAR"
    classifier = "sources"
    from(project.the<SourceSetContainer>()["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.github.haifengl"
            artifactId = "smile-kotlin"
            from(components["java"])
            artifact(sourcesJar)
            artifact(dokkaJar)
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
