plugins {
    id("buildlogic.java-common-conventions")
    id("io.quarkus")
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.7.1")
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")
    implementation("org.jboss.slf4j:slf4j-jboss-logmanager")
    testImplementation("io.quarkus:quarkus-junit5")
}

quarkus {
    buildForkOptions {
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.nio=ALL-UNNAMED",
            "---enable-native-access", "ALL-UNNAMED")
    }
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
tasks.withType<Javadoc> {
    enabled = false
}
