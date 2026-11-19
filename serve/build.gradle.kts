plugins {
    id("buildlogic.java-common-conventions")
    id("io.quarkus")
    id("jacoco-report-aggregation")
}

val quarkusPlatformGroupId = extra["quarkusPlatformGroupId"] as String
val quarkusPlatformArtifactId = extra["quarkusPlatformArtifactId"] as String
val quarkusPlatformVersion = extra["quarkusPlatformVersion"] as String

val profiles = providers.systemProperty("quarkus.profile")
    .orElse(providers.gradleProperty("quarkus.profile"))
    .orElse(providers.environmentVariable("QUARKUS_PROFILE"))
    .getOrElse("prod")
    .split(",").map(String::trim).filter(String::isNotEmpty)

val nativeBuild = providers.systemProperty("quarkus.native.enabled")
    .orElse(providers.gradleProperty("quarkus.native.enabled"))
    .getOrElse("false") == "true"

val studioLibDir = rootProject.file("studio/lib")
val hasIoaAgentJar = studioLibDir.exists() &&
    studioLibDir.listFiles { _, name -> name.startsWith("ioa-agent") && name.endsWith(".jar") }?.isNotEmpty() == true

// Enable server-side kirin agent in JVM builds when studio/lib/ioa-agent-*.jar is present
// (pass -Pserve.agent=false or -Dserve.agent=false to force the NoOpChatAgentBridge fallback).
val enableAgent = providers.gradleProperty("serve.agent")
    .orElse(providers.systemProperty("serve.agent"))
    .getOrElse("true") != "false" && hasIoaAgentJar && !nativeBuild && "micro" !in profiles

if (enableAgent) {
    sourceSets.named("main") {
        java.srcDir("src/agent/java")
    }
    sourceSets.named("test") {
        java.srcDir("src/agentTest/java")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":deep"))
    implementation(platform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.netty:netty-transport-native-epoll::linux-x86_64")
    implementation("io.netty:netty-transport-native-epoll::linux-aarch_64")
    implementation("org.jboss.slf4j:slf4j-jboss-logmanager:2.1.0.Final")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-jdbc-h2")
    if ("prod" in profiles) {
        implementation("io.quarkus:quarkus-jdbc-postgresql")
    }
    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.9.3")
    if (enableAgent) {
        implementation(project(":nlp"))
        implementation(project(":plot"))
        implementation(fileTree("$rootDir/studio/lib") { include("ioa-*.jar") })
        implementation(libs.bundles.jackson)
        implementation("com.openai:openai-java:4.105.0")
        implementation("com.anthropic:anthropic-java:3.8.0")
        implementation("com.google.genai:google-genai:2.3.0")
        implementation("io.modelcontextprotocol.sdk:mcp:2.2.0")
        implementation(libs.lsp4j)
        implementation("io.github.furstenheim:copy_down:1.1")
        implementation("org.jsoup:jsoup:1.24.1")
        implementation(libs.secret.store)
        implementation(libs.angus.mail)
    }
    // Optional plugins dropped into serve/plugins/ (default: include all; pass -Pserve.plugins=false to skip).
    if (providers.gradleProperty("serve.plugins").getOrElse("true") != "false") {
        implementation(fileTree("plugins") { include("*.jar") })
    }
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-test-h2")
    testImplementation("io.rest-assured:rest-assured:6.3.0")

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

tasks.register<Copy>("copyServeToStudio") {
    dependsOn(tasks.quarkusBuild)
    from(layout.buildDirectory.dir("quarkus-app"))
    into("$rootDir/studio/src/universal/serve")
}

tasks.build { finalizedBy("copyServeToStudio") }
