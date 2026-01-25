plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":base"))
    implementation(libs.jackson)
    implementation(libs.swingx)
}
