plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api("org.apache.commons:commons-csv:1.14.1")
    api(libs.jackson.databind) // build.sbt: databind only; the yaml dataformat is declared where it is used (studio)
    // Optional libraries, "Provided" as in base/build.sbt: needed to compile smile.data.SQL (DuckDB), smile.io (Arrow,
    // Parquet, Avro, SAS) and smile.util.lsp, but not forced on consumers. An application that uses those features
    // must declare the libraries itself, as studio does. Tests of this module get them too.
    compileOnly(libs.duckdb) // smile.data.SQL
    compileOnly(libs.bundles.arrow)
    compileOnly(libs.lsp4j)
    compileOnly(libs.parso) // SAS7BDAT
    compileOnly(libs.avro) { exclude("org.slf4j", "slf4j-log4j12") }
    compileOnly(libs.snappy) // for avro
    testImplementation(libs.duckdb)
    testImplementation(libs.bundles.arrow)
    testImplementation(libs.lsp4j)
    testImplementation(libs.parso)
    testImplementation(libs.avro) { exclude("org.slf4j", "slf4j-log4j12") }
    testImplementation(libs.snappy)
    testRuntimeOnly(libs.sqlite)
}

tasks.withType<Javadoc> {
    // Exclude generated packages from Javadoc generation
    exclude("smile/linalg/arpack/**", "smile/linalg/blas/**", "smile/linalg/lapack/**")
}
