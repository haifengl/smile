plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":core"))
    compileOnly(libs.bundles.spark)
    compileOnly(libs.hadoop.common)

    testImplementation(libs.bundles.spark)
    testImplementation(libs.hadoop.common)
}

tasks.named<Test>("test") {
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED"
    )
}
