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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import ioa.llm.Model;
import smile.util.OS;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Local LLM endpoint auto-detector ({@code L2}) and curated hardware-aware model
 * catalog ({@code L5}) for {@link SettingsDialog}.
 *
 * @author Haifeng Li
 */
public final class LocalLlmDetector {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(700);
    private static volatile HardwareProfile cachedHardware;

    /** Supported local inference runtime backends in SMILE Serve. */
    public enum RuntimeBackend {
        /** Show all curated local checkpoints. */
        ALL,
        /** ONNX Runtime GenAI packages (CPU on all platforms, DirectML on Windows). */
        ONNX_GENAI,
        /** Native LibTorch + FlashInfer + Marlin CUDA engine (Linux x86-64 NVIDIA GPU). */
        NVIDIA_CUDA
    }

    /**
     * One loopback endpoint target to probe for running OpenAI-compatible models.
     *
     * @param serviceName canonical service name to upsert in {@code llm.json} (e.g. {@code "ollama"}).
     * @param displayName human-readable server label (e.g. {@code "Ollama"}).
     * @param baseUrl     OpenAI-compatible base URL saved in {@code llm.json}.
     * @param modelsUrl   HTTP GET URL returning the JSON model list.
     */
    public record ProbeTarget(
            String serviceName,
            String displayName,
            String baseUrl,
            String modelsUrl
    ) {
    }

    /**
     * Result of a successful local endpoint probe.
     *
     * @param serviceName canonical service name (e.g. {@code "local-serve"}, {@code "ollama"}, {@code "lm-studio"}).
     * @param displayName human-readable server label.
     * @param baseUrl     OpenAI-compatible base URL.
     * @param models      live model IDs returned by the endpoint.
     */
    public record DetectedEndpoint(
            String serviceName,
            String displayName,
            String baseUrl,
            List<String> models
    ) {
    }

    /**
     * Curated SMILE Serve local LLM/SLM catalog entry with hardware and tool-profile metadata.
     *
     * @param id                Hugging Face model identifier.
     * @param backend           required inference engine backend ({@link RuntimeBackend#ONNX_GENAI} or {@link RuntimeBackend#NVIDIA_CUDA}).
     * @param paramsBillions    parameter count in billions.
     * @param memoryRequirement human-readable RAM or VRAM requirement.
     * @param summary           concise capability description.
     */
    public record CuratedModel(
            String id,
            RuntimeBackend backend,
            double paramsBillions,
            String memoryRequirement,
            String summary
    ) {
        /** Returns true if this model uses the compact tool-schema profile ({@code <=14B}). */
        public boolean usesCompactToolProfile() {
            return Model.isSmallLocalModel(id);
        }
    }

    /**
     * Detected host hardware profile for guiding local model selection.
     *
     * @param osName             operating system name.
     * @param arch               CPU architecture.
     * @param hasNvidiaGpu       true if {@code nvidia-smi} reported at least one NVIDIA GPU.
     * @param gpuSummary         GPU name and VRAM summary, or empty when no NVIDIA GPU is present.
     * @param recommendedBackend recommended default {@link RuntimeBackend} for this host.
     */
    public record HardwareProfile(
            String osName,
            String arch,
            boolean hasNvidiaGpu,
            String gpuSummary,
            RuntimeBackend recommendedBackend
    ) {
    }

    /** Curated local models verified with SMILE Serve (see {@code serve/README.md}). */
    public static final List<CuratedModel> CURATED_MODELS = List.of(
            new CuratedModel(
                    "microsoft/Phi-4-mini-instruct-onnx",
                    RuntimeBackend.ONNX_GENAI,
                    3.8,
                    "~3 GiB RAM",
                    "ONNX GenAI (CPU / Windows DirectML) • 3.8B • Compact Tool Profile (<=14B)"
            ),
            new CuratedModel(
                    "microsoft/phi-4-onnx",
                    RuntimeBackend.ONNX_GENAI,
                    14.0,
                    "~10 GiB RAM",
                    "ONNX GenAI (CPU / Windows DirectML) • 14B • Compact Tool Profile (<=14B)"
            ),
            new CuratedModel(
                    "onnx-community/Llama-3.2-3B-Instruct-GENAI-ONNX",
                    RuntimeBackend.ONNX_GENAI,
                    3.0,
                    "~2.5 GiB RAM",
                    "ONNX GenAI (CPU / Windows DirectML) • 3B • Compact Tool Profile (<=14B)"
            ),
            new CuratedModel(
                    "onnx-community/Qwen3-1.7B-ONNX",
                    RuntimeBackend.ONNX_GENAI,
                    1.7,
                    "~1.8 GiB RAM",
                    "ONNX GenAI (CPU / Windows DirectML) • 1.7B • Compact Tool Profile (<=14B)"
            ),
            new CuratedModel(
                    "hugging-quants/Meta-Llama-3.1-8B-Instruct-AWQ-INT4",
                    RuntimeBackend.NVIDIA_CUDA,
                    8.0,
                    "~6 GB VRAM",
                    "NVIDIA CUDA (Marlin AWQ INT4, 2,667 tok/s) • 8B • Compact Tool Profile (<=14B)"
            ),
            new CuratedModel(
                    "meta-llama/Llama-3.1-8B-Instruct",
                    RuntimeBackend.NVIDIA_CUDA,
                    8.0,
                    "~16 GB VRAM",
                    "NVIDIA CUDA (BF16 CUDA graphs, 2,087 tok/s) • 8B • Compact Tool Profile (<=14B)"
            ),
            new CuratedModel(
                    "Qwen/Qwen3.8-27B-FP8",
                    RuntimeBackend.NVIDIA_CUDA,
                    27.0,
                    "~29 GB VRAM (1x A100 40GB / RTX 5090)",
                    "NVIDIA CUDA (Marlin FP8 + DeltaNet + MTP) • 27B • Full Tool Profile (27B+)"
            ),
            new CuratedModel(
                    "Qwen/Qwen3.8-27B",
                    RuntimeBackend.NVIDIA_CUDA,
                    27.0,
                    "~56 GB VRAM (TP=2/4)",
                    "NVIDIA CUDA (BF16 flagship + native tool calling) • 27B • Full Tool Profile (27B+)"
            ),
            new CuratedModel(
                    "MiniMaxAI/MiniMax-M3",
                    RuntimeBackend.NVIDIA_CUDA,
                    27.0,
                    "~40 GB+ VRAM",
                    "NVIDIA CUDA (Multimodal MoE + DSpark 7-token speculation) • Full Tool Profile (27B+)"
            )
    );

    private LocalLlmDetector() {
    }

    /**
     * Returns the default local probe targets for SMILE Serve, Ollama, and LM Studio.
     *
     * @param servePort configured SMILE Serve HTTP port (default {@code 8888}).
     * @return ordered list of loopback targets to probe.
     */
    public static List<ProbeTarget> defaultTargets(int servePort) {
        int port = servePort > 0 ? servePort : StudioConfig.DEFAULT_PORT;
        return List.of(
                new ProbeTarget(
                        "local-serve",
                        "SMILE Serve",
                        "http://localhost:" + port + "/api/v1",
                        "http://127.0.0.1:" + port + "/api/v1/models"
                ),
                new ProbeTarget(
                        "ollama",
                        "Ollama",
                        "http://localhost:11434/v1",
                        "http://127.0.0.1:11434/v1/models"
                ),
                new ProbeTarget(
                        "lm-studio",
                        "LM Studio",
                        "http://localhost:1234/v1",
                        "http://127.0.0.1:1234/v1/models"
                )
        );
    }

    /**
     * Probes the default local endpoints (SMILE Serve, Ollama, LM Studio) in parallel.
     *
     * @param servePort configured SMILE Serve HTTP port.
     * @return list of responsive endpoints with their discovered model IDs.
     */
    public static List<DetectedEndpoint> detectDefaultEndpoints(int servePort) {
        return detectEndpoints(defaultTargets(servePort), DEFAULT_TIMEOUT);
    }

    /**
     * Probes the supplied targets in parallel using virtual threads.
     *
     * @param targets list of loopback targets to query.
     * @param timeout connect and read timeout per target.
     * @return list of responsive endpoints in target order.
     */
    public static List<DetectedEndpoint> detectEndpoints(List<ProbeTarget> targets, Duration timeout) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        Duration effectiveTimeout = (timeout == null || timeout.isNegative() || timeout.isZero())
                ? DEFAULT_TIMEOUT
                : timeout;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(effectiveTimeout)
                .build();
        List<DetectedEndpoint> detected = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Optional<DetectedEndpoint>>> tasks = targets.stream()
                    .<Callable<Optional<DetectedEndpoint>>>map(t -> () -> probeSingle(client, t, effectiveTimeout))
                    .toList();
            List<Future<Optional<DetectedEndpoint>>> futures = executor.invokeAll(tasks);
            for (Future<Optional<DetectedEndpoint>> f : futures) {
                try {
                    f.get().ifPresent(detected::add);
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return List.copyOf(detected);
    }

    private static Optional<DetectedEndpoint> probeSingle(
            HttpClient client,
            ProbeTarget target,
            Duration timeout) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(target.modelsUrl()))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return Optional.empty();
            }
            List<String> models = parseModelsResponse(resp.body());
            return Optional.of(new DetectedEndpoint(
                    target.serviceName(),
                    target.displayName(),
                    target.baseUrl(),
                    models
            ));
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /**
     * Parses model IDs from an OpenAI-compatible {@code /v1/models} JSON body
     * (or Ollama {@code /api/tags} JSON body). When SMILE Serve's {@code kind}
     * attribute is present on an entry, non-LLM entries ({@code SMILE}, {@code ONNX})
     * are filtered out so only chat models are returned.
     *
     * @param jsonBody raw JSON response body.
     * @return list of discovered LLM model IDs.
     */
    static List<String> parseModelsResponse(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(jsonBody);
            List<String> models = new ArrayList<>();
            if (root.has("data") && root.get("data").isArray()) {
                for (JsonNode item : root.get("data")) {
                    if (item.has("kind")) {
                        String kind = item.get("kind").asString("");
                        if (!kind.isEmpty() && !"LLM".equalsIgnoreCase(kind)) {
                            continue;
                        }
                    }
                    String id = item.has("id") ? item.get("id").asString("").trim() : "";
                    if (!id.isEmpty() && !models.contains(id)) {
                        models.add(id);
                    }
                }
            } else if (root.has("models") && root.get("models").isArray()) {
                for (JsonNode item : root.get("models")) {
                    String name = item.has("name")
                            ? item.get("name").asString("").trim()
                            : (item.has("model") ? item.get("model").asString("").trim() : "");
                    if (!name.isEmpty() && !models.contains(name)) {
                        models.add(name);
                    }
                }
            }
            return List.copyOf(models);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /**
     * Returns curated models matching the requested {@link RuntimeBackend}.
     *
     * @param backend runtime backend filter ({@link RuntimeBackend#ALL} returns all).
     * @return matching curated models.
     */
    public static List<CuratedModel> modelsForBackend(RuntimeBackend backend) {
        if (backend == null || backend == RuntimeBackend.ALL) {
            return CURATED_MODELS;
        }
        return CURATED_MODELS.stream()
                .filter(m -> m.backend() == backend)
                .toList();
    }

    /**
     * Looks up a curated model entry by its Hugging Face ID (case-insensitive).
     *
     * @param modelId Hugging Face model ID.
     * @return matching {@link CuratedModel}, or empty for custom IDs.
     */
    public static Optional<CuratedModel> findCuratedModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = modelId.trim();
        return CURATED_MODELS.stream()
                .filter(m -> m.id().equalsIgnoreCase(trimmed))
                .findFirst();
    }

    /**
     * Detects the host hardware profile (cached after first invocation).
     *
     * @return the host {@link HardwareProfile}.
     */
    public static HardwareProfile detectHardware() {
        HardwareProfile cached = cachedHardware;
        if (cached != null) {
            return cached;
        }
        String osName = System.getProperty("os.name", "Unknown");
        String arch = System.getProperty("os.arch", "Unknown");
        String gpuSummary = probeNvidiaSmi();
        boolean hasNvidia = !gpuSummary.isEmpty();
        boolean isLinux = osName.toLowerCase(Locale.ROOT).contains("linux");
        RuntimeBackend recommended = (isLinux && hasNvidia)
                ? RuntimeBackend.NVIDIA_CUDA
                : RuntimeBackend.ONNX_GENAI;
        HardwareProfile profile = new HardwareProfile(osName, arch, hasNvidia, gpuSummary, recommended);
        cachedHardware = profile;
        return profile;
    }

    private static String probeNvidiaSmi() {
        try {
            Process proc = new ProcessBuilder(
                    "nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = proc.waitFor(800, TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return "";
            }
            if (proc.exitValue() != 0) {
                return "";
            }
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String firstLine = out.lines().map(String::trim).filter(l -> !l.isEmpty()).findFirst().orElse("");
            return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }

    /**
     * Formats a concise hardware & model guidance hint for the Inference Server tab.
     *
     * @param modelId selected Hugging Face model ID (may be blank or custom).
     * @param hw      detected host hardware profile.
     * @return human-readable guidance string.
     */
    public static String describeModelAndHardware(String modelId, HardwareProfile hw) {
        String hostDesc = hw.hasNvidiaGpu()
                ? hw.osName() + " (" + hw.arch() + ", GPU: " + hw.gpuSummary() + ")"
                : hw.osName() + " (" + hw.arch() + (OS.isMacOS() ? " — use Ollama/LM Studio in AI Services for Metal/MLX GPU" : " — CPU / DirectML") + ")";
        if (modelId == null || modelId.isBlank()) {
            return hostDesc;
        }
        Optional<CuratedModel> curated = findCuratedModel(modelId);
        if (curated.isPresent()) {
            CuratedModel cm = curated.get();
            return cm.summary() + " • " + cm.memoryRequirement() + "  [" + hostDesc + "]";
        }
        boolean compact = Model.isSmallLocalModel(modelId);
        String profileLabel = compact ? "Compact SLM Tool Profile (<=14B)" : "Full LLM Tool Profile";
        return modelId.trim() + " • " + profileLabel + "  [" + hostDesc + "]";
    }
}
