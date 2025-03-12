plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(libs.bundles.javacpp)
    api("org.apache.commons:commons-csv:1.13.0")
    api("org.duckdb:duckdb_jdbc:1.2.1")
    implementation(libs.bundles.arrow)
    implementation("com.epam:parso:2.0.14") // SAS7BDAT
    implementation("org.apache.parquet:parquet-hadoop:1.15.0") { exclude("org.slf4j:slf4j-log4j12") }
    implementation("org.apache.hadoop:hadoop-common:3.4.1") { exclude("org.slf4j:slf4j-log4j12") }
    implementation("org.apache.hadoop:hadoop-mapreduce-client-core:3.4.1") { exclude("org.slf4j", "slf4j-log4j12") }
    implementation("org.apache.avro:avro:1.12.0") { exclude("org.slf4j", "slf4j-log4j12") }

    testRuntimeOnly(libs.sqlite)
}
