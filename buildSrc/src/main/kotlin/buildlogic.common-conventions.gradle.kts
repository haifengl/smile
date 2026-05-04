group = "com.github.haifengl"
version = "6.1.0"
extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

tasks.withType<Test>().all {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
    jvmArgs("-Xmx6G", "-XX:+UseG1GC", "-XX:MaxMetaspaceSize=1024M", "-Xss4M")
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED",)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Parallel streams rely on the shared ForkJoinPool.commonPool(),
    // which is automatically sized to the number of available processors minus one.
    // GitHub runners (typically 2-core) generally have fewer worker threads than dev machines,
    // Set this pool to use two threads for consistenty across machines.
    jvmArgs("-Djava.util.concurrent.ForkJoinPool.common.parallelism=2")

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
