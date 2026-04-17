plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api("org.apache.commons:commons-csv:1.14.1")
    api("org.duckdb:duckdb_jdbc:1.5.2.0")
    api(libs.jackson)
    implementation(libs.bundles.arrow)
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")
    implementation("com.epam:parso:2.0.14") // SAS7BDAT
    implementation("org.apache.avro:avro:1.12.1") { exclude("org.slf4j", "slf4j-log4j12") }
    implementation("org.xerial.snappy:snappy-java:1.1.10.8")
    testRuntimeOnly(libs.sqlite)
}

tasks.withType<Javadoc> {
    // Exclude generated packages from Javadoc generation
    exclude("smile/linalg/arpack/**", "smile/linalg/blas/**", "smile/linalg/lapack/**")
}
