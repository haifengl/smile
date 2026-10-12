plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api("org.apache.commons:commons-csv:1.14.1")
    api("org.duckdb:duckdb_jdbc:1.5.6.0")
    api(libs.jackson.databind) // build.sbt: databind only; the yaml dataformat is declared where it is used (studio)
    // Optional I/O formats, "Provided" as in base/build.sbt: needed to compile smile.io (Arrow, Parquet, Avro, SAS)
    // and smile.util.lsp, but not forced on consumers. An application that reads those formats (or runs the
    // language service) must declare the libraries itself, as studio does. Tests of this module get them too.
    compileOnly(libs.bundles.arrow)
    compileOnly("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")
    compileOnly("com.epam:parso:2.0.14") // SAS7BDAT
    compileOnly("org.apache.avro:avro:1.12.2") { exclude("org.slf4j", "slf4j-log4j12") }
    compileOnly("org.xerial.snappy:snappy-java:1.1.10.12") // for avro
    testImplementation(libs.bundles.arrow)
    testImplementation("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")
    testImplementation("com.epam:parso:2.0.14")
    testImplementation("org.apache.avro:avro:1.12.2") { exclude("org.slf4j", "slf4j-log4j12") }
    testImplementation("org.xerial.snappy:snappy-java:1.1.10.12")
    testRuntimeOnly(libs.sqlite)
}

tasks.withType<Javadoc> {
    // Exclude generated packages from Javadoc generation
    exclude("smile/linalg/arpack/**", "smile/linalg/blas/**", "smile/linalg/lapack/**")
}
