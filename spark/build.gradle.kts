plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":core"))
    compileOnly(libs.spark.core)
    compileOnly(libs.spark.sql)
    compileOnly(libs.spark.mllib)
    compileOnly(libs.hadoop.common)

    testImplementation(libs.spark.core)
    testImplementation(libs.spark.sql)
    testImplementation(libs.spark.mllib)
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
