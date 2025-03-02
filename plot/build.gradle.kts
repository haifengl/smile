plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":base"))
    implementation(libs.jackson)
    implementation("org.swinglabs:swingx:1.6.1")
}
