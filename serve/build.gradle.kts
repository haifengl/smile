plugins {
    id("buildlogic.java-common-conventions")
    id("io.quarkus")
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation(project(":deep"))
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")
    implementation("org.jboss.slf4j:slf4j-jboss-logmanager")
    /*
    implementation("org.bytedeco:pytorch-platform-gpu:2.7.1-1.5.12")
    implementation("org.bytedeco:cuda-platform-redist:12.9-9.10-1.5.12")
    implementation("org.bytedeco:cuda-platform-redist-cublas:12.9-9.10-1.5.12")
    implementation("org.bytedeco:cuda-platform-redist-cudnn:12.9-9.10-1.5.12")
    implementation("org.bytedeco:cuda-platform-redist-cusolver:12.9-9.10-1.5.12")
    implementation("org.bytedeco:cuda-platform-redist-cusparse:12.9-9.10-1.5.12")
     */
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkiverse.jdbc:quarkus-jdbc-sqlite:3.0.1")
    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.7.1")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured:6.0.0")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
tasks.quarkusDev {
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--enable-native-access", "ALL-UNNAMED")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
tasks.withType<Javadoc> {
    enabled = false
}
