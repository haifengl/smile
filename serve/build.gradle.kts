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
    // Optional plugins dropped into serve/plugins/ (default: include all; pass -Pserve.plugins=false to skip).
    if (providers.gradleProperty("serve.plugins").getOrElse("true") != "false") {
        implementation(fileTree("plugins") { include("*.jar") })
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
    systemProperty("quarkus.test.flat-class-path", "true")
}

tasks.quarkusDev {
	workingDirectory = rootProject.layout.projectDirectory.asFile
    val pluginGroup = io.quarkus.runtime.util.HashUtil.sha1(layout.projectDirectory.dir("plugins").asFile.absolutePath)
    val reloadablePlugins = layout.projectDirectory.dir("plugins").asFileTree
        .matching { include("smile-*.jar") }
        .files
        .joinToString(",") { "${pluginGroup}:${it.nameWithoutExtension}" }
    jvmArgs = buildList {
        add("--add-opens")
        add("java.base/java.lang=ALL-UNNAMED")
        add("--add-opens")
        add("java.base/java.nio=ALL-UNNAMED")
        add("--enable-native-access")
        add("ALL-UNNAMED")
        if (reloadablePlugins.isNotEmpty()) {
            add("-Dquarkus.class-loading.reloadable-artifacts=${reloadablePlugins}")
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Javadoc> {
    enabled = false
}