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

import java.nio.file.Files;
import java.nio.file.Path;
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
}
