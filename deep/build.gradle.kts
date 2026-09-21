plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":base"))
}

tasks.withType<Test>().all {
    environment("PYTORCH_ENABLE_MPS_FALLBACK", "1")
    // Opt-in flag for the SMILE_VERIFY_CUDA_GRAPH Stage 1/2 tests
    // (VerifyCudaGraphStage*Test). Harmless when CUDA/the graph feature is
    // unavailable (those tests self-skip via Assumptions) or for any other
    // test (this only changes behavior inside an active cudaStreamCapture
    // region, which nothing else here creates).
    environment("SMILE_VERIFY_CUDA_GRAPH", "1")
}

tasks.withType<Javadoc> {
    // Exclude generated packages from Javadoc generation
    exclude("smile/torch/**")
    exclude("smile/onnx/foreign/**")
    exclude("smile/onnx/genai/foreign/**")
}
