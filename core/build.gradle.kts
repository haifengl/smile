plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":base"))
}

tasks.withType<Javadoc> {
    // Exclude generated packages from Javadoc generation
    exclude("smile/onnx/foreign/**")
}
