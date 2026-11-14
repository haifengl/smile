/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import ioa.llm.client.LlmConfig;
import org.junit.jupiter.api.Test;
import smile.studio.LocalLlmDetector.CuratedModel;
import smile.studio.LocalLlmDetector.DetectedEndpoint;
import smile.studio.LocalLlmDetector.ProbeTarget;
import smile.studio.LocalLlmDetector.RuntimeBackend;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalLlmDetector} and {@link SettingsDialog} local LLM onboarding
 * ({@code L2} auto-detection and {@code L5} curated hardware-aware model selector).
 */
class LocalLlmDetectorTest {

    @Test
    void givenMockLocalServers_whenDetectEndpoints_thenDiscoversLlmModelsAndFiltersOutTabularModels() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            // Mock SMILE Serve /api/v1/models (contains SMILE, ONNX, and LLM entries)
            server.createContext("/serve/api/v1/models", ex -> {
                String body = """
                        {
                          "object": "list",
                          "data": [
                            {"id": "iris_random_forest-1", "kind": "SMILE"},
                            {"id": "logreg_iris", "kind": "ONNX"},
                            {"id": "microsoft/Phi-4-mini-instruct-onnx", "kind": "LLM"}
                          ]
                        }
                        """;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            });

            // Mock Ollama /v1/models
            server.createContext("/ollama/v1/models", ex -> {
                String body = """
                        {
                          "object": "list",
                          "data": [
                            {"id": "qwen3:8b"},
                            {"id": "phi4:14b"}
                          ]
                        }
                        """;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            });
            server.start();
            int port = server.getAddress().getPort();

            List<ProbeTarget> targets = List.of(
                    new ProbeTarget(
                            "local-serve",
                            "SMILE Serve",
                            "http://localhost:8888/api/v1",
                            "http://127.0.0.1:" + port + "/serve/api/v1/models"
                    ),
                    new ProbeTarget(
                            "ollama",
                            "Ollama",
                            "http://localhost:11434/v1",
                            "http://127.0.0.1:" + port + "/ollama/v1/models"
                    ),
                    new ProbeTarget(
                            "lm-studio",
                            "LM Studio",
                            "http://localhost:1234/v1",
                            "http://127.0.0.1:1/unreachable"
                    )
            );

            List<DetectedEndpoint> detected = LocalLlmDetector.detectEndpoints(targets, Duration.ofSeconds(2));
            assertEquals(2, detected.size());

            DetectedEndpoint serveEp = detected.get(0);
            assertEquals("local-serve", serveEp.serviceName());
            assertEquals(List.of("microsoft/Phi-4-mini-instruct-onnx"), serveEp.models());

            DetectedEndpoint ollamaEp = detected.get(1);
            assertEquals("ollama", ollamaEp.serviceName());
            assertEquals(List.of("qwen3:8b", "phi4:14b"), ollamaEp.models());

            // Verify SettingsDialog.applyDetectedEndpoints merges into LlmConfig and sets defaultService
            LlmConfig emptyCfg = LlmConfig.empty();
            LlmConfig merged = SettingsDialog.applyDetectedEndpoints(emptyCfg, detected);
            assertEquals("local-serve", merged.defaultService());
            assertTrue(merged.findService("local-serve").isPresent());
            assertEquals(List.of("microsoft/Phi-4-mini-instruct-onnx"),
                    merged.findService("local-serve").get().models());
            assertTrue(merged.findService("ollama").isPresent());
            assertEquals(List.of("qwen3:8b", "phi4:14b"),
                    merged.findService("ollama").get().models());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void givenCuratedModels_whenFilteringByBackend_thenSeparatesOnnxGenAiAndNvidiaCudaAndClassifiesToolProfile() {
        List<CuratedModel> onnxModels = LocalLlmDetector.modelsForBackend(RuntimeBackend.ONNX_GENAI);
        List<CuratedModel> cudaModels = LocalLlmDetector.modelsForBackend(RuntimeBackend.NVIDIA_CUDA);

        assertFalse(onnxModels.isEmpty());
        assertFalse(cudaModels.isEmpty());
        assertEquals(onnxModels.size() + cudaModels.size(),
                LocalLlmDetector.modelsForBackend(RuntimeBackend.ALL).size());

        // All curated ONNX GenAI CPU/DirectML models are <= 14B and use the compact tool profile
        assertTrue(onnxModels.stream().allMatch(CuratedModel::usesCompactToolProfile));

        // 8B CUDA Llama models use compact profile; 27B+ Qwen/MiniMax models use full profile
        CuratedModel llama8b = LocalLlmDetector.findCuratedModel("meta-llama/Llama-3.1-8B-Instruct").orElseThrow();
        assertTrue(llama8b.usesCompactToolProfile());

        CuratedModel qwen27b = LocalLlmDetector.findCuratedModel("Qwen/Qwen3.8-27B-FP8").orElseThrow();
        assertFalse(qwen27b.usesCompactToolProfile());

        CuratedModel minimax = LocalLlmDetector.findCuratedModel("MiniMaxAI/MiniMax-M3").orElseThrow();
        assertFalse(minimax.usesCompactToolProfile());
    }

    @Test
    void givenAllSixLocales_whenLoadingSettingsDialogBundle_thenContainsLocalAiKeys() {
        List<Locale> locales = List.of(
                Locale.ROOT,
                Locale.US,
                Locale.SIMPLIFIED_CHINESE,
                Locale.JAPAN,
                Locale.FRANCE,
                Locale.of("es", "ES")
        );
        List<String> requiredKeys = List.of(
                "DetectLocalModels",
                "DetectLocalNone",
                "DetectLocalFound",
                "ServeRuntime",
                "ServeRuntimeAll",
                "ServeRuntimeOnnx",
                "ServeRuntimeCuda"
        );
        for (Locale locale : locales) {
            ResourceBundle bundle = ResourceBundle.getBundle(SettingsDialog.class.getName(), locale);
            for (String key : requiredKeys) {
                assertTrue(bundle.containsKey(key), "Missing key '" + key + "' in locale " + locale);
                assertFalse(bundle.getString(key).isBlank(), "Blank key '" + key + "' in locale " + locale);
            }
        }
    }
}
