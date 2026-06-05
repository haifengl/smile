group = "com.github.haifengl"
version = "6.1.1"
extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

plugins {
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.withType<JacocoReport> {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<Test>().all {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
    // Parallel streams rely on the shared ForkJoinPool.commonPool(),
    // which is automatically sized to the number of available processors minus one.
    // GitHub runners (typically 2-core) generally have fewer worker threads than dev machines,
    // Set this pool to use two threads for consistenty across machines.
    systemProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2")
    jvmArgs("-Xmx6G", "-XX:MaxMetaspaceSize=1024M", "-Xss4M", "-XX:+UseZGC")
    jvmArgs("-XX:+UseCompactObjectHeaders")
    jvmArgs("-XX:+UseCompressedOops")
    jvmArgs("-XX:ObjectAlignmentInBytes=16")
    jvmArgs("-XX:+UseNUMA")
    jvmArgs("-XX:+UseStringDeduplication")
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED")
    jvmArgs("--enable-native-access=ALL-UNNAMED")

    val osName = System.getProperty("os.name").lowercase()
    val libPath = file("${rootDir.path}/studio/src/universal/bin").absolutePath
    if (osName.contains("windows")) {
        // On Windows, DLLs are found via the PATH
        val currentPath = System.getenv("PATH") ?: ""
        environment("PATH", "$libPath;$currentPath")
    } else if (osName.contains("mac")) {
        // On macOS, shared libraries (.dylib) use DYLD_LIBRARY_PATH
        val currentDyldPath = System.getenv("DYLD_LIBRARY_PATH") ?: ""
        environment("DYLD_LIBRARY_PATH", "$libPath:$currentDyldPath")
    } else {
        // On Linux, shared libraries (.so) use LD_LIBRARY_PATH
        val currentLdPath = System.getenv("LD_LIBRARY_PATH") ?: ""
        environment("LD_LIBRARY_PATH", "$libPath:$currentLdPath")
    }
}

tasks.withType<Jar>().all {
    archiveBaseName.set("${rootProject.name}-${project.name}")
}
