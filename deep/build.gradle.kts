plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":base"))
}

tasks.withType<Test>().all {
    environment("PYTORCH_ENABLE_MPS_FALLBACK", "1")
}

tasks.withType<Javadoc> {
    // Exclude generated packages from Javadoc generation
    exclude("smile/torch/**")
}
