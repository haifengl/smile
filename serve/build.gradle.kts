plugins {
    id("buildlogic.java-common-conventions")
    id("io.quarkus")
    id("jacoco-report-aggregation")
}

val quarkusPlatformGroupId = extra["quarkusPlatformGroupId"] as String
val quarkusPlatformArtifactId = extra["quarkusPlatformArtifactId"] as String
val quarkusPlatformVersion = extra["quarkusPlatformVersion"] as String

dependencies {
    implementation(project(":core"))
    implementation(project(":deep"))
    implementation(platform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("org.jboss.slf4j:slf4j-jboss-logmanager:2.1.0.Final")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-jdbc-h2")
    val profiles = providers.systemProperty("quarkus.profile")
        .orElse(providers.gradleProperty("quarkus.profile"))
        .orElse(providers.environmentVariable("QUARKUS_PROFILE"))
        .getOrElse("prod")
        .split(",").map(String::trim).filter(String::isNotEmpty)
    if ("prod" in profiles) {
        implementation("io.quarkus:quarkus-jdbc-postgresql")
    }
    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.9.2")
    implementation(project(":serve-plugins:blob-spi"))
    // Optional plugins, chosen at build time: -Pserve.plugins=blob-s3,... (default: all; empty for native).
    val availablePlugins = listOf("blob-s3")
    val plugins = providers.gradleProperty("serve.plugins").orNull
        ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty) ?: availablePlugins
    plugins.forEach {
        require(it in availablePlugins) { "Unknown serve plugin '$it'; available: $availablePlugins" }
        implementation(project(":serve-plugins:$it"))
    }
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-test-h2")
    testImplementation("io.rest-assured:rest-assured:6.1.0")

    // JaCoCo aggregated report
    jacocoAggregation(project(":base"))
    jacocoAggregation(project(":core"))
    jacocoAggregation(project(":deep"))
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