plugins {
    id("buildlogic.java-library-conventions")
}

val quarkusPlatformGroupId = extra["quarkusPlatformGroupId"] as String
val quarkusPlatformArtifactId = extra["quarkusPlatformArtifactId"] as String
val quarkusPlatformVersion = extra["quarkusPlatformVersion"] as String

dependencies {
    api(project(":serve-plugins:blob-spi"))
    implementation(platform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-arc")
    implementation(platform("software.amazon.awssdk:bom:2.55.11"))
    implementation("software.amazon.awssdk:s3")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Javadoc> {
    enabled = false
}
