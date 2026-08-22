/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SubmissionPublisher;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import org.jboss.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.*;
import smile.llm.attention.AttentionBackend;
import smile.llm.attention.AttentionBackends;
import smile.llm.attention.FlashInferArtifacts;
import smile.llm.checkpoint.SafeTensorsLoaderThreads;
import smile.llm.model.llama.*;
import smile.llm.model.qwen.Qwen;
import smile.util.HuggingFaceHub;

/**
 * Application-scoped service that loads a Llama LLM and handles chat
 * completion requests.
 *
 * <p>The model is loaded once at application startup from the location
 * configured by {@code smile.chat.model}:
 * <ul>
 *   <li>If the value is an existing local directory, the model is loaded
 *       directly from that path.</li>
 *   <li>Else if the value looks like a Hugging Face Hub repository ID
 *       ({@code owner/name}), the required model files are downloaded to
 *       the local HF cache before loading.</li>
 *   <li>Otherwise the chat service stays unavailable (no HF download is
 *       attempted for filesystem-like paths).</li>
 * </ul>
 *
 * <p>If the model cannot be loaded, the service starts in an
 * <em>unavailable</em> state and every chat request returns HTTP 503.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class ChatService {
    private static final Logger logger = Logger.getLogger(ChatService.class);

    /** The loaded LLM; {@code null} when the model failed to load. */
    private LanguageModel model;
    /** Request-oriented runtime; {@code null} when no model is loaded. */
    private smile.llm.engine.InferenceEngine engine;
    /**
     * Public model id exposed by the chat API (HF repo id or local directory
     * name). Independent of family-prefixed {@code toString()} labels.
     */
    private final String modelId;
    /** OpenAI {@code owned_by} value for the loaded model. */
    private String ownedBy = ModelObject.UNKNOWN_OWNER;
    /** Unix epoch seconds when the model finished loading. */
    private long createdAt;
    /** {@code "huggingface"} or {@code "local"} when a model is loaded. */
    private String source;

    /**
     * Loads the LLM upon application start.
     * The {@code @ApplicationScoped} scope ensures the model is loaded once and reused.
     *
     * <p>After weights (and Qwen DeltaNet state) are loaded, a shared
     * {@link smile.llm.cache.KvCachePool} is sized with SGLang
     * {@code mem-fraction-static} semantics via
     * {@link ChatServiceConfig#memFractionStatic()}: {@code y × total} for the
     * static region, with KV getting the remainder inside that budget. The pool
     * element dtype comes from {@link KvCacheConfig#dtype()} when set, otherwise
     * from the model's {@code config.json} {@code torch_dtype}.
     *
     * @param config  the chat service configuration.
     * @param kvCache KV-cache storage configuration.
     */
    @Inject
    public ChatService(ChatServiceConfig config, KvCacheConfig kvCache) {
        String modelSpec = config.model();
        this.modelId = publicModelId(modelSpec);
        try {
            String cacheDir = config.flashinferCacheDir()
                    .filter(s -> !s.isBlank())
                    .orElseGet(() -> Path.of(System.getProperty("user.home"),
                            ".cache", "smile", "flashinfer").toString());
            FlashInferArtifacts.resolveAndInstall(
                    config.flashinferAotDir().orElse(null),
                    cacheDir,
                    config.flashinferDownload(),
                    config.flashinferCudaTag());
            AttentionBackend requested = AttentionBackend.parse(config.attentionBackend());
            AttentionBackends.install(requested);
            if (requested == AttentionBackend.FLASHINFER
                    && AttentionBackends.current() != AttentionBackend.FLASHINFER
                    && !config.flashinferAllowTorchFallback()) {
                throw new IllegalStateException(
                        "FlashInfer requested but unavailable and flashinfer-allow-torch-fallback=false");
            }
            double memFraction = config.memFractionStatic();
            String kvDtype = kvCache.dtype().orElse(null);
            int pageSize = kvCache.pageSize();
            Path localPath = Path.of(modelSpec);
            if (Files.isDirectory(localPath)) {
                model = loadFromLocal(localPath, config, memFraction, kvDtype, pageSize);
                ownedBy = ownerFromFamily(model.family());
                source = "local";
                createdAt = Instant.now().getEpochSecond();
            } else if (looksLikeHuggingFaceRepoId(modelSpec)) {
                model = loadFromHuggingFace(config, memFraction, kvDtype, pageSize);
                ownedBy = ownerFromHuggingFaceId(modelSpec);
                source = "huggingface";
                createdAt = Instant.now().getEpochSecond();
            } else {
                logger.warnf("Chat model '%s' is neither a local directory nor a Hugging Face "
                        + "repository ID; chat completions will return HTTP 503", modelSpec);
            }
            if (model != null) {
                applyPrefixReuse(model, kvCache.prefixReuse());
                if (model instanceof smile.llm.engine.ModelExecutor exec) {
                    engine = new smile.llm.engine.InferenceEngine(exec, Math.max(1, config.maxBatchSize()));
                }
                logger.infof("Chat model ready: id=%s family=%s maxSeqLen=%d (config max-seq-len=%d) maxInFlight=%d",
                        modelId, model.family(), model.maxSeqLen(), config.maxSeqLen(),
                        engine != null ? engine.maxInFlight() : 1);
            }
        } catch (Exception ex) {
            // Keep the service up in an unavailable state so classic ML / ONNX
            // endpoints still work; chat requests return HTTP 503.
            logger.warnf(ex, "Failed to load chat model '%s'; chat completions will return HTTP 503",
                    modelSpec);
            model = null;
            engine = null;
        }
    }

    @PreDestroy
    void shutdown() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }

    /**
     * Applies {@code smile.chat.kv-cache.prefix-reuse} to the loaded chat model.
     */
    static void applyPrefixReuse(LanguageModel model, boolean enabled) {
        switch (model) {
            case Llama llama -> llama.setPrefixReuseEnabled(enabled);
            case Qwen qwen -> qwen.setPrefixReuseEnabled(enabled);
            default -> { }
        }
    }

    /**
     * Derives the public API model id from {@code smile.chat.model}.
     *
     * <ul>
     *   <li>Local directory → final path segment (e.g. {@code Llama3.1-8B-Instruct})</li>
     *   <li>Hugging Face repo id → as configured (e.g. {@code Qwen/Qwen2.5-7B-Instruct})</li>
     * </ul>
     *
     * @param modelSpec the configured {@code smile.chat.model} value.
     * @return the public model id.
     */
    static String publicModelId(String modelSpec) {
        if (modelSpec == null || modelSpec.isBlank()) {
            return "unknown";
        }
        String spec = modelSpec.trim();
        // Prefer Hub repo ids over Files.isDirectory — a relative path such as
        // "meta-llama/Llama-3.1-8B-Instruct" may accidentally exist on disk.
        if (looksLikeHuggingFaceRepoId(spec)) {
            return spec;
        }
        Path path = Path.of(spec);
        if (Files.isDirectory(path)) {
            Path fileName = path.getFileName();
            return fileName != null ? fileName.toString() : spec;
        }
        return spec;
    }

    /**
     * Returns {@code true} when {@code spec} looks like a Hugging Face Hub
     * repository ID ({@code owner/name}), not a filesystem path.
     *
     * <p>Rejects absolute paths, relative path prefixes ({@code ./}, {@code ../}),
     * Windows drive letters, and multi-segment paths such as
     * {@code serve/src/test/resources/...}.
     *
     * @param spec the configured {@code smile.chat.model} value.
     * @return {@code true} if the value should be resolved via Hugging Face Hub.
     */
    static boolean looksLikeHuggingFaceRepoId(String spec) {
        if (spec == null || spec.isBlank()) return false;
        String s = spec.trim();
        if (s.startsWith("/") || s.startsWith(".") || s.contains("\\") || s.contains(":")) {
            return false;
        }
        int slash = s.indexOf('/');
        if (slash <= 0 || slash != s.lastIndexOf('/') || slash == s.length() - 1) {
            return false;
        }
        return true;
    }

    /**
     * Returns {@code true} if the LLM model is loaded and ready.
     *
     * @return {@code true} if available.
     */
    public boolean isAvailable() {
        return model != null;
    }

    /**
     * Returns the public model id used in chat completion requests/responses.
     *
     * <p>This is the configured Hugging Face repo id or local directory name —
     * not a Meta/Llama-specific prefix — so future non-Llama models keep a
     * stable client-facing id.
     *
     * @return the model id, or {@code "unknown"} when the model is not loaded.
     */
    public String modelName() {
        return model != null ? modelId : "unknown";
    }

    /**
     * Returns OpenAI-shaped descriptors for currently loaded chat models.
     *
     * @return a singleton list when a model is loaded; otherwise empty.
     */
    public List<ModelObject> listModels() {
        return findOpenAiModel(modelId, false).map(List::of).orElseGet(List::of);
    }

    /**
     * Looks up the loaded chat model as an OpenAI {@link ModelObject}.
     *
     * @param id       the requested model id.
     * @param detailed when {@code true}, include {@link LlmModelDetails}.
     * @return the model object when loaded and ids match; otherwise empty.
     */
    public Optional<ModelObject> findOpenAiModel(String id, boolean detailed) {
        if (model == null || id == null || id.isBlank()) {
            return Optional.empty();
        }
        if (!modelId.equals(id.trim())) {
            return Optional.empty();
        }
        LlmModelDetails llm = detailed ? LlmModelDetails.of(model, source) : null;
        return Optional.of(ModelObject.of(modelId, createdAt, ownedBy, ModelObject.KIND_LLM,
                null, null, llm));
    }

    /**
     * Looks up the loaded chat model as a lean OpenAI {@link ModelObject}.
     *
     * @param id the requested model id.
     * @return the model object when loaded and ids match; otherwise empty.
     */
    public Optional<ModelObject> findOpenAiModel(String id) {
        return findOpenAiModel(id, false);
    }

    /**
     * Derives {@code owned_by} from a Hugging Face repo id ({@code owner/name}).
     *
     * @param repoId the Hugging Face repository id.
     * @return the owner segment, or the whole id when no slash is present.
     */
    static String ownerFromHuggingFaceId(String repoId) {
        if (repoId == null || repoId.isBlank()) {
            return ModelObject.UNKNOWN_OWNER;
        }
        String id = repoId.trim();
        int slash = id.indexOf('/');
        return slash > 0 ? id.substring(0, slash) : id;
    }

    /**
     * Derives {@code owned_by} from {@link Llama#family()} for locally loaded
     * checkpoints (first path segment, e.g. {@code meta} from {@code meta/llama3}).
     *
     * @param family the model family label.
     * @return the first segment of the family string.
     */
    static String ownerFromFamily(String family) {
        if (family == null || family.isBlank()) {
            return ModelObject.UNKNOWN_OWNER;
        }
        String f = family.trim();
        int slash = f.indexOf('/');
        return slash > 0 ? f.substring(0, slash) : f;
    }

    /**
     * Returns {@code true} when {@code requested} may be served by the loaded model.
     *
     * <p>{@code null}, blank, or omitted requests are accepted and use the loaded
     * model. Otherwise the value must equal {@link #modelName()}.
     *
     * @param requested the {@code model} field from the chat completion request.
     * @return {@code true} if the request may proceed.
     */
    public boolean acceptsModel(String requested) {
        return matchesModelId(requested, modelName());
    }

    /**
     * Pure matching helper for {@link #acceptsModel(String)}.
     *
     * @param requested    request {@code model} value.
     * @param loadedModelId currently loaded model id from {@link #modelName()}.
     * @return {@code true} when the request should be accepted.
     */
    static boolean matchesModelId(String requested, String loadedModelId) {
        if (requested == null || requested.isBlank()) {
            return true;
        }
        return requested.trim().equals(loadedModelId);
    }

    /**
     * Completes a chat dialog via {@link smile.llm.engine.InferenceEngine}.
     *
     * @param request   the chat completion request.
     * @param publisher the flow publisher that receives streamed token chunks.
     * @return the completion result for the request.
     */
    public ChatCompletion complete(CompletionRequest request, SubmissionPublisher<String> publisher) {
        var handle = submitCompletion(request, publisher);
        try {
            return handle.future().join();
        } finally {
            if (publisher != null) {
                publisher.close();
            }
        }
    }

    /**
     * Submits a chat completion and returns the handle so streaming callers can
     * {@link smile.llm.engine.GenerationHandle#abort()} on client disconnect.
     * Does not close {@code publisher}; the caller owns that lifecycle.
     *
     * @param request   the chat completion request.
     * @param publisher optional streaming publisher; may be {@code null}.
     * @return generation handle whose future completes with the reply.
     */
    public smile.llm.engine.GenerationHandle submitCompletion(
            CompletionRequest request, SubmissionPublisher<String> publisher) {
        int[] prompt = model.encodeChat(request.messages);
        int maxGenLen = request.resolveMaxTokens(model.maxSeqLen(), prompt.length);
        var throughput = new TokenThroughputLogger();
        var listener = GenerationListeners.compose(
                throughput,
                publisher != null ? GenerationListeners.toPublisher(publisher) : null);
        if (engine != null) {
            var handle = engine.submit(smile.llm.engine.GenerationRequest.ofTokens(
                    prompt, maxGenLen, request.temperature, request.topP,
                    request.logprobs, request.seed, listener));
            handle.future().whenComplete((r, t) -> throughput.finish());
            return handle;
        }
        var future = new java.util.concurrent.CompletableFuture<ChatCompletion>();
        var handle = smile.llm.engine.GenerationHandle.of(0L, future);
        try {
            ChatCompletion result = model.generate(prompt, maxGenLen, request.temperature,
                    request.topP, request.logprobs, request.seed, listener, handle::isAborted);
            if (!handle.isAborted()) {
                future.complete(result);
            } else {
                future.completeExceptionally(
                        new java.util.concurrent.CancellationException("aborted"));
            }
        } catch (Throwable t) {
            future.completeExceptionally(t);
        } finally {
            throughput.finish();
        }
        return handle;
    }

    /**
     * Loads a chat model from a local checkpoint directory.
     * Dispatches on {@code config.json} {@code model_type} / {@code architectures}.
     */
    private LanguageModel loadFromLocal(Path localPath, ChatServiceConfig config,
                                        double memFraction, String kvDtype, int pageSize)
            throws Exception {
        if (isQwenCheckpoint(localPath)) {
            var parallel = parallelConfig(config);
            return Qwen.build(localPath.toString(),
                    config.maxBatchSize(), config.maxSeqLen(), parallel.devices()[0],
                    memFraction, kvDtype, pageSize, parallel, config.modelLoaderThreads());
        }
        String tokenizerPath = resolveLocalTokenizer(localPath);
        return Llama.build(localPath.toString(), tokenizerPath,
                config.maxBatchSize(), config.maxSeqLen(), parallelConfig(config).devices()[0],
                memFraction, kvDtype, pageSize, config.modelLoaderThreads());
    }

    /**
     * Builds a {@link smile.llm.parallel.ParallelConfig} from chat settings.
     *
     * <p>{@code smile.chat.devices} is either a single index ({@code 0}) or a
     * comma-separated TP list ({@code 0,7}). With one device and
     * {@code tensor-parallel-size=N>1}, consecutive devices
     * {@code d .. d+N-1} are used.
     */
    static smile.llm.parallel.ParallelConfig parallelConfig(ChatServiceConfig config) {
        int pp = config.pipelineParallelSize();
        if (pp != 1) {
            throw new IllegalArgumentException(
                    "smile.chat.pipeline-parallel-size must be 1 until PP is implemented");
        }

        byte[] devices = parseDevices(config);
        if (devices.length <= 1) {
            return smile.llm.parallel.ParallelConfig.single(devices[0]);
        }
        int tp = config.tensorParallelSize();
        if (tp > 1 && tp != devices.length) {
            throw new IllegalArgumentException(
                    "smile.chat.devices length (" + devices.length
                            + ") must equal smile.chat.tensor-parallel-size (" + tp + ")");
        }
        return smile.llm.parallel.ParallelConfig.tensorParallel(devices);
    }

    /**
     * Parses {@code smile.chat.devices}. A single value is the base device;
     * with {@code tensor-parallel-size > 1} it expands to consecutive indices.
     * Multiple comma-separated values are the explicit TP device list.
     */
    static byte[] parseDevices(ChatServiceConfig config) {
        String raw = config.devices();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("smile.chat.devices must not be blank");
        }
        String[] parts = raw.split(",");
        byte[] parsed = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            try {
                int idx = Integer.parseInt(part);
                if (idx < Byte.MIN_VALUE || idx > Byte.MAX_VALUE) {
                    throw new IllegalArgumentException("device index out of byte range: " + idx);
                }
                parsed[i] = (byte) idx;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid smile.chat.devices entry '" + part
                                + "' (expected integer or comma-separated list, e.g. 0 or 0,7)", e);
            }
        }
        if (parsed.length > 1) {
            return parsed;
        }

        // Single base device: expand to consecutive ranks when tp > 1.
        int tp = Math.max(1, config.tensorParallelSize());
        if (tp == 1) {
            return parsed;
        }
        byte[] devices = new byte[tp];
        for (int i = 0; i < tp; i++) {
            devices[i] = (byte) (parsed[0] + i);
        }
        return devices;
    }

    /**
     * Returns {@code true} when {@code config.json} identifies a Qwen3.5 hybrid checkpoint.
     */
    static boolean isQwenCheckpoint(Path checkpointDir) throws IOException {
        Path configJson = checkpointDir.resolve("config.json");
        if (!Files.isRegularFile(configJson)) {
            return false;
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(configJson.toFile());
        String modelType = root.has("model_type") ? root.get("model_type").asString() : "";
        if (modelType.startsWith("qwen3_5")) {
            return true;
        }
        if (root.has("architectures") && root.get("architectures").isArray()) {
            for (JsonNode n : root.get("architectures")) {
                String arch = n.asString();
                if (arch != null && arch.startsWith("Qwen3_5")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Downloads HuggingFace-format model files and returns a loaded language model.
     *
     * <p>Downloads {@code config.json}, {@code model.safetensors.index.json} (when
     * present), every safetensors shard listed in the index, and the tokenizer
     * files required by the architecture (SentencePiece for Llama, vocab/merges
     * or {@code tokenizer.json} for Qwen).
     *
     * @param config the chat service configuration; {@code config.model()} is the HF repo ID.
     * @param memFractionStatic static-region fraction of total GPU memory (SGLang-style).
     * @param kvCacheDtype optional KV-cache dtype override ({@code null} = auto).
     * @param pageSize tokens per radix / KV pool page.
     * @return the loaded language model.
     * @throws Exception if a required file cannot be downloaded or the model fails to load.
     */
    private LanguageModel loadFromHuggingFace(ChatServiceConfig config, double memFractionStatic,
                                              String kvCacheDtype, int pageSize) throws Exception {
        String repoId = config.model();
        logger.infof("Model directory '%s' not found locally. Downloading from Hugging Face Hub...", repoId);

        Path configPath = HuggingFaceHub.download(repoId, "config.json");
        String checkpointDir = configPath.getParent().toString();
        logger.infof("Downloaded config.json to %s", checkpointDir);

        Set<String> shards = resolveSafeTensorShards(repoId);
        downloadSafeTensorShards(repoId, shards, config.modelLoaderThreads());

        Path checkpoint = Path.of(checkpointDir);
        if (isQwenCheckpoint(checkpoint)) {
            resolveHuggingFaceQwenTokenizer(repoId);
            var parallel = parallelConfig(config);
            return Qwen.build(checkpointDir,
                    config.maxBatchSize(), config.maxSeqLen(), parallel.devices()[0],
                    memFractionStatic, kvCacheDtype, pageSize, parallel,
                    config.modelLoaderThreads());
        }

        String tokenizerPath = resolveHuggingFaceTokenizer(repoId);
        return Llama.build(checkpointDir, tokenizerPath,
                config.maxBatchSize(), config.maxSeqLen(), parallelConfig(config).devices()[0],
                memFractionStatic, kvCacheDtype, pageSize, config.modelLoaderThreads());
    }

    /**
     * Downloads Qwen tokenizer files ({@code tokenizer.json}, and optionally
     * {@code vocab.json}/{@code merges.txt}).
     */
    private void resolveHuggingFaceQwenTokenizer(String repoId) throws IOException {
        String[] candidates = {"tokenizer.json", "vocab.json", "merges.txt"};
        boolean any = false;
        for (String name : candidates) {
            try {
                Path path = HuggingFaceHub.download(repoId, name);
                logger.infof("Downloaded tokenizer file: %s", path);
                any = true;
            } catch (Exception ex) {
                logger.debugf("Optional tokenizer file %s not found in %s", name, repoId);
            }
        }
        if (!any) {
            throw new IOException("No Qwen tokenizer files found in Hugging Face repository: " + repoId);
        }
    }

    /**
     * Downloads safetensors shards concurrently. Thread count matches
     * {@link ChatServiceConfig#modelLoaderThreads()} via
     * {@link SafeTensorsLoaderThreads#resolve(int, int)}.
     */
    private void downloadSafeTensorShards(String repoId, Set<String> shards, int modelLoaderThreads)
            throws IOException {
        if (shards == null || shards.isEmpty()) {
            return;
        }
        List<String> shardList = new ArrayList<>(shards);
        int threads = SafeTensorsLoaderThreads.resolve(modelLoaderThreads, shardList.size());
        logger.infof("Downloading %d safetensors shard(s) with threads=%d (configured=%d)",
                shardList.size(), threads, modelLoaderThreads);

        if (threads <= 1 || shardList.size() == 1) {
            for (String shard : shardList) {
                logger.infof("Downloading safetensors shard: %s", shard);
                HuggingFaceHub.download(repoId, shard);
            }
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>(shardList.size());
            for (String shard : shardList) {
                futures.add(pool.submit(() -> {
                    try {
                        logger.infof("Downloading safetensors shard: %s", shard);
                        HuggingFaceHub.download(repoId, shard);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    if (c instanceof RuntimeException re && re.getCause() instanceof IOException ioe) {
                        throw ioe;
                    }
                    throw new IOException("Safetensors shard download failed", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Resolves the list of safetensors shard filenames for a HuggingFace repo.
     * Prefers {@code model.safetensors.index.json}; falls back to a single
     * {@code model.safetensors} file.
     */
    private Set<String> resolveSafeTensorShards(String repoId) throws IOException {
        Set<String> shards = new LinkedHashSet<>();
        try {
            Path indexPath = HuggingFaceHub.download(repoId, "model.safetensors.index.json");
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(indexPath.toFile());
            JsonNode weightMap = root.get("weight_map");
            if (weightMap == null || !weightMap.isObject()) {
                throw new IOException("Invalid model.safetensors.index.json: missing weight_map");
            }
            for (var entry : weightMap.properties()) {
                shards.add(entry.getValue().asString());
            }
            return shards;
        } catch (FileNotFoundException e) {
            logger.debugf("No model.safetensors.index.json in %s; trying model.safetensors", repoId);
        }

        // Single-file checkpoint.
        HuggingFaceHub.download(repoId, "model.safetensors");
        shards.add("model.safetensors");
        return shards;
    }

    /**
     * Finds a SentencePiece tokenizer under a local HF-layout checkpoint directory.
     *
     * <p>Tries {@code original/tokenizer.model} (Llama 3+) then {@code tokenizer.model}.
     *
     * @param checkpointDir local model directory.
     * @return absolute path to the tokenizer file.
     * @throws IOException if neither candidate exists.
     */
    static String resolveLocalTokenizer(Path checkpointDir) throws IOException {
        String[] candidates = {"original/tokenizer.model", "tokenizer.model"};
        for (String candidate : candidates) {
            Path path = checkpointDir.resolve(candidate);
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath().normalize().toString();
            }
        }
        throw new IOException("tokenizer.model not found under checkpoint directory: " + checkpointDir);
    }

    /**
     * Downloads a SentencePiece tokenizer from a Hugging Face repo.
     *
     * <p>Tries {@code original/tokenizer.model} (Llama 3+) then {@code tokenizer.model}.
     *
     * @param repoId Hugging Face repository id.
     * @return path to the downloaded tokenizer file.
     * @throws IOException if neither candidate can be downloaded.
     */
    private String resolveHuggingFaceTokenizer(String repoId) throws IOException {
        String[] candidates = {"original/tokenizer.model", "tokenizer.model"};
        for (String candidate : candidates) {
            try {
                Path path = HuggingFaceHub.download(repoId, candidate);
                if (!Files.isRegularFile(path)) {
                    logger.warnf("Tokenizer candidate '%s' is not a readable file: %s", candidate, path);
                    continue;
                }
                logger.infof("Downloaded tokenizer: %s", path);
                return path.toString();
            } catch (FileNotFoundException ignored) {
                logger.debugf("Tokenizer not found at '%s'", candidate);
            }
        }
        throw new IOException("tokenizer.model not found in Hugging Face repository: " + repoId);
    }
}
