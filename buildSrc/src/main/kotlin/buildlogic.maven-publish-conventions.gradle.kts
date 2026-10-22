plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.github.haifengl"
            artifactId = "${rootProject.name}-${project.name}"
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
                val isServeModule = project.path.startsWith(":serve")
                name = "${rootProject.name}-${project.name}"
                description = "Statistical Machine Intelligence and Learning Engine"
                url = "https://www.aihalo.dev/"
                licenses {
                    if (isServeModule) {
                        license {
                            name = "AGPL-3.0-or-later"
                            url = "https://www.gnu.org/licenses/agpl-3.0.html"
                            distribution = "repo"
                        }
                    } else {
                        license {
                            name = "GPL-3.0-or-later"
                            url = "https://www.gnu.org/licenses/gpl-3.0.html"
                            distribution = "repo"
                        }
                    }
                    license {
                        name = "LicenseRef-SMILE-Commercial"
                        url = "https://www.aihalo.dev/licensing.html"
                        distribution = "repo"
                        comments = "Commercial and OEM licenses available through HALO.AI LLC (sales@aihalo.dev)"
                    }
                }
                developers {
                    developer {
                        id = "haifengl"
                        name = "Haifeng Li"
                        url = "https://www.aihalo.dev/"
                    }
                    developer {
                        id = "kklioss"
                        name = "Karl Li"
                        url = "https://github.com/kklioss"
                    }
                }
                scm {
                    connection = "scm:git:git@github.com:haifengl/smile.git"
                    developerConnection = "scm:git:git@github.com:haifengl/smile.git"
                    url = "https://github.com/haifengl/smile"
                }
            }
        }
    }
    repositories {
        maven {
            val nexusUsername = System.getenv("NEXUS_USERNAME") ?: project.findProperty("nexusUsername") as String?
            val nexusPassword = System.getenv("NEXUS_PASSWORD") ?: project.findProperty("nexusPassword") as String?
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            val snapshotsRepoUrl = uri("https://central.sonatype.com/repositories/maven-snapshots")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = nexusUsername
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

