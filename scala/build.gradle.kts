plugins {
    id("buildlogic.scala-library-conventions")
}

dependencies {
    implementation(project(":nlp"))
    implementation(project(":plot"))
    implementation(project(":json"))
    implementation("org.scala-lang.modules:scala-xml_3:2.3.0")
}
