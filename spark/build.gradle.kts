plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":core"))
    compileOnly(libs.bundles.spark)
    testImplementation(libs.bundles.spark)
}

tasks.named<Test>("test") {
    // Cap Gradle's test worker JVM memory
    minHeapSize = "512m"
    maxHeapSize = "2g" // 2GB max heap is safe for local Spark tests

    // Prevent Gradle from running multiple test classes concurrently on the same runner
    maxParallelForks = 1

    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED"
    )
}
