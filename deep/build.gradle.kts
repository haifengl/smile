plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":base"))
    implementation(libs.jackson)
    implementation("org.bytedeco:pytorch-platform:2.7.1-1.5.12")
    implementation("org.bytedeco:cuda-platform:12.9-9.10-1.5.12")
}

tasks.withType<Test>().all {
    environment("PYTORCH_ENABLE_MPS_FALLBACK", "1")
    systemProperty("org.bytedeco.javacpp.pathsFirst", "true")
    systemProperty("java.library.path", "serve/src/universal/torch/lib")
}
