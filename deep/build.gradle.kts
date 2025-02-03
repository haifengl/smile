plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":base"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.2")
    implementation("ai.djl.sentencepiece:sentencepiece:0.31.1")
    implementation("org.bytedeco:pytorch-platform:2.5.1-1.5.11")
    implementation("org.bytedeco:cuda-platform:12.6-9.5-1.5.11")
}

tasks.withType<Test>().all {
    environment("PYTORCH_ENABLE_MPS_FALLBACK", "1")
    systemProperty("org.bytedeco.javacpp.pathsFirst", "true")
    systemProperty("java.library.path", "serve/src/universal/torch/li")
}

tasks.jar {
    manifest {
        attributes["Automatic-Module-Name"] = "smile.deep"
    }
}
