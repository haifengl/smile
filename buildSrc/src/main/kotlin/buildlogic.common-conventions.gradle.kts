group = "com.github.haifengl"
version = "5.0.2"
extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

tasks.withType<Test>().all {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
    jvmArgs("-Xmx6G", "-XX:+UseG1GC", "-XX:MaxMetaspaceSize=1024M", "-Xss4M")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/sun.nio.cs=ALL-UNNAMED", "--add-opens=java.base/sun.security.action=ALL-UNNAMED")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Jar>().all {
    archiveBaseName.set("${rootProject.name}-${project.name}")
}

