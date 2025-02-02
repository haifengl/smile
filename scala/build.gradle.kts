plugins {
    id("buildlogic.scala-library-conventions")
}

dependencies {
    val scalaBinVersion = "2.13"
    implementation(project(":nlp"))
    implementation(project(":plot"))
    implementation(project(":json"))
    implementation("org.scala-lang.modules:scala-xml_$scalaBinVersion:2.3.0")
}
