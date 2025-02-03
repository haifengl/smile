plugins {
    `maven-publish`
    signing
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
                name.set("${rootProject.name}-${project.name}")
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
    sign(configurations.archives.get())
    sign(publishing.publications["mavenJava"])
}
