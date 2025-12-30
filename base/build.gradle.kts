plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api("org.apache.commons:commons-csv:1.14.1")
    api("org.duckdb:duckdb_jdbc:1.4.3.0")
    implementation(libs.bundles.arrow)
    implementation("com.epam:parso:2.0.14") // SAS7BDAT
    implementation("org.apache.avro:avro:1.12.1") { exclude("org.slf4j", "slf4j-log4j12") }

    testRuntimeOnly(libs.sqlite)
}

tasks.withType<Javadoc> {
    // Exclude specific packages from Javadoc generation
    exclude("smile/linalg/arpack/**", "smile/linalg/blas/**", "smile/linalg/lapack/**")
}
