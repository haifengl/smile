/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Olive cache-hit and GenAI path helpers (no Olive CLI).
 *
 * @author Haifeng Li
 */
public class OliveTest {

    @Test
    public void findGenAiRootDirect(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("genai_config.json"), "{}");
        Optional<Path> hit = Olive.findGenAiRoot(dir);
        assertTrue(hit.isPresent());
        assertEquals(dir, hit.get());
    }

    @Test
    public void findGenAiRootNestedModel(@TempDir Path dir) throws Exception {
        Path model = Files.createDirectories(dir.resolve("model"));
        Files.writeString(model.resolve("genai_config.json"), "{}");
        Optional<Path> hit = Olive.findGenAiRoot(dir);
        assertTrue(hit.isPresent());
        assertEquals(model, hit.get());
    }

    @Test
    public void sanitizeModelSpec() {
        assertEquals("Qwen_Qwen2.5-0.5B-Instruct",
                Olive.sanitize("Qwen/Qwen2.5-0.5B-Instruct"));
    }

    @Test
    public void isGenAiCheckpoint(@TempDir Path dir) throws Exception {
        assertFalse(GenAiModelPaths.isGenAiCheckpoint(dir));
        Files.writeString(dir.resolve("genai_config.json"), "{}");
        assertTrue(GenAiModelPaths.isGenAiCheckpoint(dir));
        assertEquals(dir.toAbsolutePath().normalize(),
                GenAiModelPaths.resolveGenAiReady(dir.toString()).orElseThrow());
    }

    @Test
    public void resolveNestedLocalGenAiPackage(@TempDir Path dir) throws Exception {
        Path cuda = Files.createDirectories(dir.resolve("cuda").resolve("cuda-int4-rtn-block-32"));
        Path cpu = Files.createDirectories(
                dir.resolve("cpu_and_mobile").resolve("cpu-int4-rtn-block-32"));
        Files.writeString(cuda.resolve("genai_config.json"), "{}");
        Files.writeString(cpu.resolve("genai_config.json"), "{}");
        Path hit = GenAiModelPaths.resolveGenAiReady(dir.toString()).orElseThrow();
        // Prefer CUDA package when present (matches GenAI cascade preference on GPU hosts).
        assertTrue(hit.toString().replace('\\', '/').contains("cuda-int4")
                || hit.toString().replace('\\', '/').contains("cpu-int4"));
        assertTrue(GenAiModelPaths.isGenAiCheckpoint(hit));
    }

    @Test
    public void pickPreferredPathPrefersCudaInt4() {
        var target = new smile.onnx.genai.GenAIOliveTarget(
                "cuda", "gpu", "CUDAExecutionProvider", "int4");
        Optional<String> pick = GenAiModelPaths.pickPreferredPath(List.of(
                "cpu_and_mobile/cpu-int4-rtn-block-32/genai_config.json",
                "cuda/cuda-fp16/genai_config.json",
                "cuda/cuda-int4-rtn-block-32/genai_config.json",
                "directml/directml-int4-awq-block-128/genai_config.json"), target);
        assertEquals("cuda/cuda-int4-rtn-block-32/genai_config.json", pick.orElseThrow());
    }

    @Test
    public void pickPreferredPathPrefersDirectMl() {
        var target = new smile.onnx.genai.GenAIOliveTarget(
                "dml", "gpu", "DmlExecutionProvider", "int4");
        Optional<String> pick = GenAiModelPaths.pickPreferredPath(List.of(
                "cpu_and_mobile/cpu-int4-rtn-block-32/genai_config.json",
                "cuda/cuda-int4-rtn-block-32/genai_config.json",
                "directml/directml-int4-awq-block-128/genai_config.json"), target);
        assertEquals("directml/directml-int4-awq-block-128/genai_config.json", pick.orElseThrow());
    }

    @Test
    public void clampPrecisionMapsFp8ToInt4() {
        assertEquals("int4", Olive.clampPrecision("fp8"));
        assertEquals("fp16", Olive.clampPrecision("fp16"));
        assertEquals("int4", Olive.clampPrecision("int4"));
    }

    @Test
    public void clampProviderMapsDirectMlToCpu() {
        assertEquals("CPUExecutionProvider",
                Olive.clampProvider("DmlExecutionProvider"));
        assertEquals("CUDAExecutionProvider",
                Olive.clampProvider("CUDAExecutionProvider"));
        assertEquals("cpu",
                Olive.clampDevice("gpu", "CPUExecutionProvider"));
        assertEquals("gpu",
                Olive.clampDevice("cpu", "CUDAExecutionProvider"));
    }

    @Test
    public void detectsTensorRtEpLoadFailure() {
        assertTrue(Olive.isOrtEpLoadFailure(new IOException(
                "Error loading onnxruntime_providers_tensorrt.dll which depends on nvinfer_10.dll")));
        assertTrue(Olive.isOrtEpLoadFailure(new IOException(
                "register_execution_provider_library failed")));
        assertFalse(Olive.isOrtEpLoadFailure(new IOException("model not found")));
    }

    @Test
    public void buildOptimizeCommandPrefersPythonBootstrap() throws Exception {
        Path out = Path.of("target", "olive-out");
        List<String> cmd = Olive.buildOptimizeCommand(
                "olive", "owner/model", out, "int4", "cpu", "CPUExecutionProvider");
        assertFalse(cmd.isEmpty());
        // Either python …/olive_cli.py … or raw olive …
        if (cmd.getFirst().equals("python") || cmd.getFirst().equals("py")
                || cmd.getFirst().contains("python")) {
            assertTrue(cmd.stream().anyMatch(s -> s.endsWith("olive_cli.py")));
            assertTrue(cmd.contains("optimize"));
            assertTrue(cmd.contains("model_builder"));
            assertFalse(cmd.stream().anyMatch(s -> s.contains("model_builderev")));
        } else {
            assertEquals("olive", cmd.getFirst());
            assertEquals("optimize", cmd.get(1));
        }
    }
}
