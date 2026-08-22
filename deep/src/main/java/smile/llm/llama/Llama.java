/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.llama;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.deep.layer.ParameterInit;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.cache.KvCachePool;
import smile.llm.checkpoint.SafeTensorsLoaderThreads;
import smile.torch.smile_torch_h;
import smile.util.AutoScope;

/**
 * LLaMA model specification.
 *
 * @author Haifeng Li
 */
public class Llama implements LanguageModel, smile.llm.engine.ModelExecutor {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Llama.class);
    /**
     * Architecture family label for this implementation.
     * Not the public chat API model id — serve uses {@code smile.chat.model}
     * (HF repo id or directory name) so non-Llama models are not forced under
     * a Meta prefix.
     */
    static final String family = "meta/llama3";
    /** Matches HuggingFace layer weight names such as {@code model.layers.12.self_attn.q_proj.weight}. */
    private static final Pattern HF_LAYER_WEIGHT = Pattern.compile(
            "^model\\.layers\\.(\\d+)\\.(self_attn|mlp|input_layernorm|post_attention_layernorm)\\.(.+)$");
    /** The model instance name. */
    final String name;
    /** The transformer model. */
    final LlamaModel model;
    /** The tokenizer. */
    final Tokenizer tokenizer;
    /** Hyperparameters loaded from the checkpoint. */
    final LlamaModelArgs params;

    /**
     * Constructor.
     * @param name the model name.
     * @param model the transformer model.
     * @param tokenizer the tokenizer.
     * @param params the model hyperparameters.
     */
    public Llama(String name, LlamaModel model, Tokenizer tokenizer, LlamaModelArgs params) {
        this.name = name;
        this.model = model;
        this.tokenizer = tokenizer;
        this.params = params;
    }

    @Override
    public String toString() {
        return String.format("%s/%s", family, name);
    }

    @Override
    public String family() {
        return family;
    }

    /**
     * Enables or disables radix prefix reuse for all KV pools on this model.
     *
     * @param enabled {@code true} to match/insert prefixes across requests.
     */
    public void setPrefixReuseEnabled(boolean enabled) {
        model.kvCachePool().setPrefixReuseEnabled(enabled);
    }

    /**
     * Returns the transformer hyperparameters loaded from the checkpoint.
     *
     * @return model args from {@code config.json} / {@code params.json}.
     */
    public LlamaModelArgs params() {
        return params;
    }

    /**
     * Returns the model instance name.
     * @return the model instance name.
     */
    @Override
    public String name() {
        return name;
    }

    @Override
    public int maxSeqLen() {
        return params.maxSeqLen();
    }

    @Override
    public int[] encodeChat(Message... dialog) {
        return tokenizer.encodeDialog(dialog);
    }

    /**
     * Builds a Llama instance by initializing and loading a model checkpoint.
     *
     * <p>Supports two on-disk layouts:
     * <ul>
     *   <li><b>Meta</b> — {@code params.json} plus {@code consolidated.*.pt} shards.</li>
     *   <li><b>HuggingFace</b> — {@code config.json} plus {@code *.safetensors}
     *       (optionally indexed by {@code model.safetensors.index.json}).</li>
     * </ul>
     *
     * @param checkpointDir the directory path of checkpoint files.
     * @param tokenizerPath the path of tokenizer model file.
     * @param maxBatchSize the maximum batch size for inference.
     * @param maxSeqLen the maximum sequence length for input text.
     * @param deviceId the optional CUDA device ID. If negative, don't use CUDA.
     * @throws IOException if fail to open model checkpoint.
     * @return an instance of Llama model.
     */
    public static Llama build(String checkpointDir, String tokenizerPath, int maxBatchSize, int maxSeqLen, byte deviceId) throws IOException {
        return build(checkpointDir, tokenizerPath, maxBatchSize, maxSeqLen, deviceId, 0, null);
    }

    /**
     * Builds a Llama instance by initializing and loading a model checkpoint.
     *
     * <p>When {@code memFractionStatic > 0}, a {@link KvCachePool} is allocated
     * after weight loading with SGLang {@code --mem-fraction-static} semantics:
     * {@code y} is a fraction of <em>total</em> GPU memory for the static region
     * (weights + KV); see {@code smile.chat.mem-fraction-static} in smile-serve.
     *
     * @param checkpointDir the directory path of checkpoint files.
     * @param tokenizerPath the path of tokenizer model file.
     * @param maxBatchSize the maximum batch size for inference.
     * @param maxSeqLen the maximum sequence length for input text.
     * @param deviceId the optional CUDA device ID. If negative, don't use CUDA.
     * @param memFractionStatic static-region fraction of total GPU memory for
     *                          weights + KV; {@code <= 0} keeps the default test-sized pool.
     * @throws IOException if fail to open model checkpoint.
     * @return an instance of Llama model.
     */
    public static Llama build(String checkpointDir, String tokenizerPath, int maxBatchSize,
                              int maxSeqLen, byte deviceId, double memFractionStatic) throws IOException {
        return build(checkpointDir, tokenizerPath, maxBatchSize, maxSeqLen, deviceId,
                memFractionStatic, null);
    }

    /**
     * Builds a Llama instance by initializing and loading a model checkpoint.
     *
     * <p>When {@code memFractionStatic > 0}, a {@link KvCachePool} is allocated
     * after weight loading with SGLang {@code --mem-fraction-static} semantics:
     * {@code y} is a fraction of <em>total</em> GPU memory for the static region
     * (weights + KV); see {@code smile.chat.mem-fraction-static} in smile-serve.
     *
     * @param checkpointDir the directory path of checkpoint files.
     * @param tokenizerPath the path of tokenizer model file.
     * @param maxBatchSize the maximum batch size for inference.
     * @param maxSeqLen the maximum sequence length for input text.
     * @param deviceId the optional CUDA device ID. If negative, don't use CUDA.
     * @param memFractionStatic static-region fraction of total GPU memory for
     *                          weights + KV; {@code <= 0} keeps the default test-sized pool.
     * @param kvCacheDtype optional KV-cache element dtype name
     *                     (e.g. {@code bfloat16}, {@code float16});
     *                     {@code null}/blank uses {@code torch_dtype} from
     *                     {@code config.json}, then the CUDA compute dtype.
     * @throws IOException if fail to open model checkpoint.
     * @return an instance of Llama model.
     */
    public static Llama build(String checkpointDir, String tokenizerPath, int maxBatchSize,
                              int maxSeqLen, byte deviceId, double memFractionStatic,
                              String kvCacheDtype) throws IOException {
        return build(checkpointDir, tokenizerPath, maxBatchSize, maxSeqLen, deviceId,
                memFractionStatic, kvCacheDtype, KvCachePool.DEFAULT_PAGE_SIZE);
    }

    /**
     * Builds a Llama instance by initializing and loading a model checkpoint.
     *
     * <p>When {@code memFractionStatic > 0}, a {@link KvCachePool} is allocated
     * after weight loading with SGLang {@code --mem-fraction-static} semantics:
     * {@code y} is a fraction of <em>total</em> GPU memory for the static region
     * (weights + KV); see {@code smile.chat.mem-fraction-static} in smile-serve.
     *
     * @param checkpointDir the directory path of checkpoint files.
     * @param tokenizerPath the path of tokenizer model file.
     * @param maxBatchSize the maximum batch size for inference.
     * @param maxSeqLen the maximum sequence length for input text.
     * @param deviceId the optional CUDA device ID. If negative, don't use CUDA.
     * @param memFractionStatic static-region fraction of total GPU memory for
     *                          weights + KV; {@code <= 0} keeps the default test-sized pool.
     * @param kvCacheDtype optional KV-cache element dtype name
     *                     (e.g. {@code bfloat16}, {@code float16});
     *                     {@code null}/blank uses {@code torch_dtype} from
     *                     {@code config.json}, then the CUDA compute dtype.
     * @param pageSize tokens per radix / KV pool page ({@code >= 1}).
     * @throws IOException if fail to open model checkpoint.
     * @return an instance of Llama model.
     */
    public static Llama build(String checkpointDir, String tokenizerPath, int maxBatchSize,
                              int maxSeqLen, byte deviceId, double memFractionStatic,
                              String kvCacheDtype, int pageSize) throws IOException {
        return build(checkpointDir, tokenizerPath, maxBatchSize, maxSeqLen, deviceId,
                memFractionStatic, kvCacheDtype, pageSize, 0);
    }

    /**
     * Builds a Llama instance by initializing and loading a model checkpoint.
     *
     * <p>When {@code memFractionStatic > 0}, a {@link KvCachePool} is allocated
     * after weight loading with SGLang {@code --mem-fraction-static} semantics:
     * {@code y} is a fraction of <em>total</em> GPU memory for the static region
     * (weights + KV); see {@code smile.chat.mem-fraction-static} in smile-serve.
     *
     * @param checkpointDir the directory path of checkpoint files.
     * @param tokenizerPath the path of tokenizer model file.
     * @param maxBatchSize the maximum batch size for inference.
     * @param maxSeqLen the maximum sequence length for input text.
     * @param deviceId the optional CUDA device ID. If negative, don't use CUDA.
     * @param memFractionStatic static-region fraction of total GPU memory for
     *                          weights + KV; {@code <= 0} keeps the default test-sized pool.
     * @param kvCacheDtype optional KV-cache element dtype name
     *                     (e.g. {@code bfloat16}, {@code float16});
     *                     {@code null}/blank uses {@code torch_dtype} from
     *                     {@code config.json}, then the CUDA compute dtype.
     * @param pageSize tokens per radix / KV pool page ({@code >= 1}).
     * @param modelLoaderThreads safetensors loader threads; {@code 0} = auto
     *                           ({@link smile.llm.checkpoint.SafeTensorsLoaderThreads#resolve}).
     * @throws IOException if fail to open model checkpoint.
     * @return an instance of Llama model.
     */
    public static Llama build(String checkpointDir, String tokenizerPath, int maxBatchSize,
                              int maxSeqLen, byte deviceId, double memFractionStatic,
                              String kvCacheDtype, int pageSize, int modelLoaderThreads)
            throws IOException {
        File dir = new File(checkpointDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Checkpoint directory doesn't exist: " + checkpointDir);
        }

        String worldSize = Objects.requireNonNullElse(System.getenv("WORLD_SIZE"), "1");
        int modelParallelSize = Integer.parseInt(worldSize);
        String localRank = Objects.requireNonNullElse(System.getenv("LOCAL_RANK"), "0");
        int rank = Integer.parseInt(localRank);

        Device device = Device.CPU();
        ScalarType computeDtype = ScalarType.Float;
        if (deviceId >= 0) {
            var startTime = System.currentTimeMillis();
            device = Device.CUDA(deviceId);

            // half precision to lower memory usage for weights / activations.
            computeDtype = Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half;
            smile_torch_h.smile_set_default_dtype(computeDtype.code());
            var time = System.currentTimeMillis() - startTime;
            logger.info("Initialized CUDA[{}]: {}.{} seconds", deviceId, time / 1000, time % 1000);
        }

        var options = new Tensor.Options().device(device).requireGradients(false);
        Tensor.setDefaultOptions(options);

        var startTime = System.currentTimeMillis();
        Path configJson = Path.of(checkpointDir, "config.json");
        Path paramsJson = Path.of(checkpointDir, "params.json");
        boolean huggingFace = Files.exists(configJson)
                && (Files.exists(Path.of(checkpointDir, "model.safetensors.index.json"))
                    || !getSafeTensorFiles(dir).isEmpty());

        LlamaModelArgs modelArgs;
        if (huggingFace) {
            modelArgs = LlamaModelArgs.fromHuggingFace(configJson.toString(), maxBatchSize, maxSeqLen);
        } else if (Files.exists(paramsJson)) {
            modelArgs = LlamaModelArgs.from(paramsJson.toString(), maxBatchSize, maxSeqLen);
        } else if (Files.exists(configJson)) {
            huggingFace = true;
            modelArgs = LlamaModelArgs.fromHuggingFace(configJson.toString(), maxBatchSize, maxSeqLen);
        } else {
            throw new IllegalArgumentException(
                    "Neither params.json nor config.json found in " + checkpointDir);
        }
        if (maxSeqLen <= 0) {
            logger.info("max-seq-len auto-resolved to {} from model config (request override was {})",
                    modelArgs.maxSeqLen(), maxSeqLen);
        } else {
            logger.info("max-seq-len={} (explicit)", modelArgs.maxSeqLen());
        }

        ScalarType cacheDtype = resolveKvCacheDtype(kvCacheDtype, configJson, computeDtype);
        logger.info("KV cache dtype: {} (override={}, compute={})",
                cacheDtype, kvCacheDtype, computeDtype);

        var tokenizer = Tokenizer.of(tokenizerPath);
        if (tokenizer.size() != modelArgs.vocabSize()) {
            throw new IllegalStateException("Tokenizer and LlamaModelArgs have different vocabulary size.");
        }

        var layout = modelArgs.kvCacheLayout();
        long tConstruct = System.currentTimeMillis();
        LlamaModel model;
        try (var ignored = ParameterInit.uninitialized(device)) {
            model = newModel(modelArgs);
        }
        logger.info("LlamaModel construct in {} ms (layers={}, maxSeqLen={})",
                System.currentTimeMillis() - tConstruct, modelArgs.numLayers(), modelArgs.maxSeqLen());
        // Place empty module + cis before load so torch checkpoints land on device
        // and HF loadStateDict targets match model.device(); cis moves with to().
        long tTo = System.currentTimeMillis();
        model.to(device);
        logger.info("model.to({}) in {} ms", device, System.currentTimeMillis() - tTo);

        long tLoad = System.currentTimeMillis();
        if (huggingFace) {
            loadHuggingFaceWeights(model, modelArgs, dir, modelLoaderThreads);
        } else {
            List<String> checkpoints = getCheckpoints(dir);
            if (checkpoints.isEmpty()) {
                throw new IllegalArgumentException("No checkpoint files found in " + checkpointDir);
            }
            if (checkpoints.size() != modelParallelSize) {
                throw new IllegalStateException(String.format(
                        "Loading a checkpoint for MP=%d but world size is %d",
                        checkpoints.size(), modelParallelSize));
            }
            Collections.sort(checkpoints);
            model.load(checkpoints.get(rank));
        }
        logger.info("Weight load in {} ms", System.currentTimeMillis() - tLoad);
        model.eval();
        model.setRequiresGrad(false);

        // Size KV after weights are on device (staticBudget − used when mem-fraction set).
        long tKv = System.currentTimeMillis();
        device.emptyCache();
        KvCachePool pool = memFractionStatic > 0
                ? KvCachePool.allocate(layout, device, cacheDtype, memFractionStatic, pageSize)
                : KvCachePool.forTesting(layout, device);
        model.setKvCachePool(pool, false);
        logger.info("KvCachePool allocate in {} ms", System.currentTimeMillis() - tKv);

        var time = System.currentTimeMillis() - startTime;
        logger.info("Model {}[{}]: loaded in {}.{} seconds", checkpointDir, rank, time/1000, time%1000);
        return new Llama(dir.getName(), model, tokenizer, modelArgs);
    }

    /**
     * Builds a {@link LlamaModel} on CPU (call {@link LlamaModel#to} then install KV).
     */
    static LlamaModel newModel(LlamaModelArgs args) {
        return new LlamaModel(
                args.dim(),
                args.numLayers(),
                args.numHeads(),
                args.resolvedNumKvHeads(),
                args.vocabSize(),
                args.intermediateSize(),
                args.multipleOf(),
                args.ffnDimMultiplier(),
                args.normEps(),
                args.ropeTheta(),
                args.scaledRope(),
                args.maxSeqLen());
    }

    /**
     * Resolves the KV-cache element dtype.
     *
     * <ol>
     *   <li>Explicit override from {@code smile.chat.kv-cache.dtype} when non-blank</li>
     *   <li>{@code torch_dtype} in HuggingFace {@code config.json} when present</li>
     *   <li>{@code fallback} (CUDA compute dtype or float32 on CPU)</li>
     * </ol>
     *
     * @param override optional configured dtype name.
     * @param configJson path to {@code config.json} (may not exist).
     * @param fallback dtype used when neither override nor config specify one.
     * @return the resolved dtype.
     * @throws IOException if {@code config.json} exists but cannot be read.
     */
    static ScalarType resolveKvCacheDtype(String override, Path configJson, ScalarType fallback)
            throws IOException {
        if (override != null && !override.isBlank()) {
            return parseDtypeName(override);
        }
        ScalarType fromConfig = torchDtypeFromConfig(configJson);
        return fromConfig != null ? fromConfig : fallback;
    }

    /**
     * Reads {@code torch_dtype} from a HuggingFace {@code config.json}, if present.
     *
     * @param configJson path to {@code config.json}.
     * @return the parsed dtype, or {@code null} when absent / file missing.
     * @throws IOException if the file exists but cannot be parsed.
     */
    private static ScalarType torchDtypeFromConfig(Path configJson) throws IOException {
        if (configJson == null || !Files.isRegularFile(configJson)) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(configJson.toFile());
        JsonNode dtype = root.get("torch_dtype");
        if (dtype == null || dtype.isNull() || dtype.asString().isBlank()) {
            return null;
        }
        return parseDtypeName(dtype.asString());
    }

    /**
     * Parses a human-readable floating-point dtype name.
     *
     * @param name dtype name (case-insensitive), e.g. {@code bfloat16}, {@code float16}.
     * @return the corresponding {@link ScalarType}.
     */
    public static ScalarType parseDtypeName(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        // Normalize separators: fp8-e4m3, fp8_e4m3fn, torch.float8_e5m2, etc.
        key = key.replace('-', '_');
        if (key.startsWith("torch.")) {
            key = key.substring("torch.".length());
        }
        return switch (key) {
            case "bfloat16", "bf16" -> ScalarType.BFloat16;
            case "float16", "fp16", "half" -> ScalarType.Half;
            case "float32", "fp32", "float" -> ScalarType.Float;
            case "float64", "fp64", "double" -> ScalarType.Double;
            case "fp8_e4m3", "fp8_e4m3fn", "float8_e4m3", "float8_e4m3fn", "float8e4m3fn"
                    -> ScalarType.Float8e4m3fn;
            case "fp8_e5m2", "float8_e5m2", "float8e5m2"
                    -> ScalarType.Float8e5m2;
            case "fp8_e4m3fnuz", "float8_e4m3fnuz", "float8e4m3fnuz"
                    -> ScalarType.Float8e4m3fnuz;
            case "fp8_e5m2fnuz", "float8_e5m2fnuz", "float8e5m2fnuz"
                    -> ScalarType.Float8e5m2fnuz;
            default -> throw new IllegalArgumentException(
                    "Unsupported KV cache dtype '" + name
                            + "'; expected bfloat16, float16, float32, float64, "
                            + "fp8_e4m3, fp8_e5m2, fp8_e4m3fnuz, or fp8_e5m2fnuz");
        };
    }

    /**
     * Returns the Meta-format {@code .pt} checkpoint file paths.
     * @param dir the checkpoint directory.
     * @return the checkpoint file paths.
     */
    private static List<String> getCheckpoints(File dir) {
        List<String> checkpoints = new ArrayList<>();
        var files = dir.listFiles();
        if (files == null) return checkpoints;
        for (var file : files) {
            var path = file.getPath();
            if (path.endsWith(".pt")) {
                checkpoints.add(path);
            }
        }
        return checkpoints;
    }

    /**
     * Returns safetensors shard file names present in {@code dir}.
     * @param dir the checkpoint directory.
     * @return sorted list of {@code *.safetensors} file names (not full paths).
     */
    private static List<String> getSafeTensorFiles(File dir) {
        List<String> files = new ArrayList<>();
        var listed = dir.listFiles();
        if (listed == null) return files;
        for (var file : listed) {
            if (file.isFile() && file.getName().endsWith(".safetensors")) {
                files.add(file.getName());
            }
        }
        Collections.sort(files);
        return files;
    }

    /**
     * Loads HuggingFace safetensors weights into {@code model}.
     *
     * <p>Each shard file is read once onto CPU; loader concurrency is
     * {@link SafeTensorsLoaderThreads#resolve}. Weight names are remapped from
     * the HuggingFace convention to the Meta / SMILE convention. Query and key
     * projection weights are reverse-permuted for Meta-style RoPE.
     */
    private static void loadHuggingFaceWeights(LlamaModel model, LlamaModelArgs args,
                                               File dir, int modelLoaderThreads) throws IOException {
        Map<String, String> weightMap = readWeightMap(dir);
        Map<String, List<String>> shardToKeys = new LinkedHashMap<>();
        for (var entry : weightMap.entrySet()) {
            shardToKeys.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        List<String> shardFiles = new ArrayList<>(shardToKeys.keySet());
        Collections.sort(shardFiles);

        int threads = SafeTensorsLoaderThreads.resolve(modelLoaderThreads, shardFiles.size());
        logger.info("Safetensors loader threads={} (configured={}, shards={})",
                threads, modelLoaderThreads, shardFiles.size());

        int numHeads = args.numHeads();
        int numKvHeads = args.resolvedNumKvHeads();
        Set<String> loaded = ConcurrentHashMap.newKeySet();
        Object modelLock = new Object();
        Device loadDevice = Device.CPU();
        Device target = model.device();
        boolean needTiedOutput = !weightMap.containsKey("lm_head.weight");

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
        try {
            List<Future<?>> futures = new ArrayList<>(shardFiles.size());
            for (String shardFile : shardFiles) {
                List<String> keys = shardToKeys.get(shardFile);
                futures.add(pool.submit(() -> {
                    try {
                        loadOneLlamaShard(model, dir, loadDevice, target, shardFile, keys,
                                numHeads, numKvHeads, modelLock, loaded, needTiedOutput, weightMap);
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
                    throw new IOException("Safetensors shard load failed", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        logger.info("Loaded {} parameters from HuggingFace safetensors", loaded.size());
    }

    private static void loadOneLlamaShard(LlamaModel model, File dir, Device loadDevice, Device target,
                                          String shardFile, List<String> keys,
                                          int numHeads, int numKvHeads, Object modelLock,
                                          Set<String> loaded, boolean needTiedOutput,
                                          Map<String, String> weightMap) throws IOException {
        Path shardPath = Path.of(dir.getPath(), shardFile);
        logger.info("Loading safetensors shard: {}", shardFile);
        long t0 = System.currentTimeMillis();
        SafeTensors st = SafeTensors.read(shardPath.toString(), loadDevice, keys);
        try {
            synchronized (modelLock) {
                Map<String, Tensor> stateDict = new HashMap<>();
                List<Tensor> owned = new ArrayList<>();
                try {
                    for (String hfName : keys) {
                        Tensor src = st.tensors().get(hfName);
                        if (src == null) {
                            throw new IOException("Tensor '" + hfName + "' missing from " + shardFile);
                        }
                        String smileName = remapHuggingFaceName(hfName);
                        if (smileName == null) {
                            logger.debug("Skipping unrecognized HF weight: {}", hfName);
                            continue;
                        }

                        Tensor value = src;
                        if (smileName.endsWith(".attention.wq.weight")) {
                            value = reversePermute(src, numHeads);
                            owned.add(value);
                        } else if (smileName.endsWith(".attention.wk.weight")) {
                            value = reversePermute(src, numKvHeads);
                            owned.add(value);
                        }
                        Tensor onDevice = value.to(target);
                        if (onDevice != value) {
                            owned.add(onDevice);
                        }
                        Tensor contiguous = onDevice.contiguous();
                        if (contiguous != onDevice) {
                            owned.add(contiguous);
                        }
                        stateDict.put(smileName, contiguous);
                        loaded.add(smileName);
                    }

                    if (needTiedOutput && !stateDict.containsKey("output.weight")
                            && st.tensors().containsKey("model.embed_tokens.weight")
                            && !weightMap.containsKey("lm_head.weight")) {
                        Tensor onDevice = st.tensors().get("model.embed_tokens.weight").to(target);
                        owned.add(onDevice);
                        Tensor emb = onDevice.contiguous();
                        if (emb != onDevice) {
                            owned.add(emb);
                        }
                        stateDict.put("output.weight", emb);
                        loaded.add("output.weight");
                    }

                    if (!stateDict.isEmpty()) {
                        model.loadStateDict(stateDict, false);
                    }
                } finally {
                    for (Tensor t : owned) {
                        t.close();
                    }
                }
            }
        } finally {
            for (Tensor t : st.tensors().values()) {
                t.close();
            }
        }
        logger.info("Loaded safetensors shard: {} in {} ms",
                shardFile, System.currentTimeMillis() - t0);
    }

    /**
     * Reads the shard weight map from {@code model.safetensors.index.json}, or
     * synthesises a single-shard map when only standalone {@code *.safetensors}
     * files are present.
     */
    private static Map<String, String> readWeightMap(File dir) throws IOException {
        Path indexPath = Path.of(dir.getPath(), "model.safetensors.index.json");
        if (Files.exists(indexPath)) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(indexPath.toFile());
            JsonNode weightMap = root.get("weight_map");
            if (weightMap == null || !weightMap.isObject()) {
                throw new IOException("Invalid model.safetensors.index.json: missing weight_map");
            }
            Map<String, String> map = new LinkedHashMap<>();
            for (var entry : weightMap.properties()) {
                map.put(entry.getKey(), entry.getValue().asString());
            }
            return map;
        }

        List<String> shards = getSafeTensorFiles(dir);
        if (shards.isEmpty()) {
            throw new IOException("No safetensors files found in " + dir);
        }
        if (shards.size() > 1) {
            throw new IOException(
                    "Multiple safetensors files found but no model.safetensors.index.json in " + dir);
        }

        // Single-file checkpoint: discover tensor names from the file header
        // without materialising multi-gigabyte weight data.
        String shard = shards.getFirst();
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : SafeTensors.listTensors(Path.of(dir.getPath(), shard).toString())) {
            map.put(name, shard);
        }
        return map;
    }

    /**
     * Maps a HuggingFace parameter name to the Meta / SMILE module name.
     *
     * @param hfName the HuggingFace weight name.
     * @return the SMILE parameter name, or {@code null} if the weight is unused.
     */
    static String remapHuggingFaceName(String hfName) {
        if ("model.embed_tokens.weight".equals(hfName)) {
            return "tok_embeddings.weight";
        }
        if ("model.norm.weight".equals(hfName)) {
            return "norm.weight";
        }
        if ("lm_head.weight".equals(hfName)) {
            return "output.weight";
        }

        Matcher m = HF_LAYER_WEIGHT.matcher(hfName);
        if (!m.matches()) {
            return null;
        }
        String layer = m.group(1);
        String component = m.group(2);
        String rest = m.group(3);

        return switch (component) {
            case "self_attn" -> switch (rest) {
                case "q_proj.weight" -> "layers." + layer + ".attention.wq.weight";
                case "k_proj.weight" -> "layers." + layer + ".attention.wk.weight";
                case "v_proj.weight" -> "layers." + layer + ".attention.wv.weight";
                case "o_proj.weight" -> "layers." + layer + ".attention.wo.weight";
                default -> null;
            };
            case "mlp" -> switch (rest) {
                case "gate_proj.weight" -> "layers." + layer + ".feed_forward.w1.weight";
                case "down_proj.weight" -> "layers." + layer + ".feed_forward.w2.weight";
                case "up_proj.weight" -> "layers." + layer + ".feed_forward.w3.weight";
                default -> null;
            };
            case "input_layernorm" -> "layers." + layer + ".attention_norm.weight";
            case "post_attention_layernorm" -> "layers." + layer + ".ffn_norm.weight";
            default -> null;
        };
    }

    /**
     * Undoes the HuggingFace Q/K permutation applied when converting Meta
     * checkpoints, restoring the layout expected by Meta-style RoPE.
     *
     * <p>{@code w.view(n_heads, 2, head_dim/2, dim).transpose(1, 2).reshape(...)}
     *
     * @param w the HuggingFace projection weight {@code [out_features, in_features]}.
     * @param nHeads the number of attention heads for this projection.
     * @return a new contiguous tensor in Meta layout (caller owns it).
     */
    static Tensor reversePermute(Tensor w, int nHeads) {
        long dim1 = w.shape()[0];
        long dim2 = w.shape()[1];
        long headDim = dim1 / nHeads;
        try (Tensor viewed = w.view(nHeads, 2, headDim / 2, dim2);
             Tensor transposed = viewed.transpose(1, 2);
             Tensor cont = transposed.contiguous();
             Tensor reshaped = cont.reshape(dim1, dim2)) {
            // Clone so the returned tensor owns its storage independently of
            // the temporary views closed by this try-with-resources.
            return reshaped.copy();
        }
    }

    /**
     * Generates a text completion from a tokenized prompt using nucleus sampling.
     *
     * @param prompt      tokenized prompt.
     * @param maxGenLen   maximum number of new tokens to generate.
     * @param temperature temperature value for controlling randomness in sampling.
     * @param topp        top-p probability threshold for nucleus sampling.
     * @param logprobs    flag indicating whether to compute token log probabilities.
     * @param seed        optional RNG seed to sample deterministically; {@code 0} is non-deterministic.
     * @param listener    optional generation progress callback.
     * @return the generated text completion.
     */
    @Override
    public ChatCompletion generate(int[] prompt, int maxGenLen, double temperature,
                                   double topp, boolean logprobs, long seed,
                                   GenerationListener listener) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }

        int promptLen = prompt.length;
        if (promptLen > params.maxSeqLen()) {
            throw new IllegalArgumentException("The prompt length is greater than max_seq_len");
        }
        // Cap prompt + max_tokens by max-seq-len.
        int maxAllowedGen = Math.max(0, params.maxSeqLen() - promptLen);
        if (maxGenLen > maxAllowedGen) {
            maxGenLen = maxAllowedGen;
        }
        if (maxGenLen < 0) {
            maxGenLen = 0;
        }

        // seed must be the same in all processes
        if (seed != 0) {
            smile_torch_h.smile_manual_seed(seed);
        }

        try (var guard = Tensor.noGradGuard();
             var scope = new AutoScope()) {
            Tensor.push(scope);
            try {
            int desiredTotalLen = Math.min(params.maxSeqLen(), maxGenLen + promptLen);
            int prefixLen = model.kvCachePool().bindWithPrefix(prompt, desiredTotalLen);
            int totalLen = Math.min(desiredTotalLen, model.kvCachePool().requestCapacity());
            if (totalLen < promptLen) {
                throw new IllegalArgumentException(String.format(
                        "Prompt length %d exceeds free KV capacity %d",
                        promptLen, totalLen));
            }
            final int cachedPrefixTokens = prefixLen;
            // Keep the last prompt token in the first forward so we obtain
            // next-token logits when generation is needed.
            if (prefixLen > 0 && promptLen < totalLen && promptLen > 0) {
                prefixLen = Math.min(prefixLen, promptLen - 1);
            }
            if (listener != null) {
                listener.onInputTokens(promptLen);
                listener.onCachedInputTokens(Math.min(cachedPrefixTokens, promptLen));
            }

            int pad = tokenizer.pad();
            Tensor tokensCpu = Tensor.full(pad, 1, totalLen);
            try (var promptTensor = Tensor.of(prompt);
                 var row = Index.of(0);
                 var span = Index.slice(0, promptLen)) {
                tokensCpu.put_(promptTensor, row, span);
            }

            Tensor tokenLogprobs = null;
            if (logprobs) {
                var opts = new Tensor.Options().device(model.device()).requireGradients(false).dtype(ScalarType.Float);
                tokenLogprobs = Tensor.zeros(opts, 1, totalLen);
            }

            Tensor eosReachedCpu = Tensor.of(new boolean[1]);
            Tensor inputTextMaskCpu = tokensCpu.ne(pad);
            Tensor stopTokensCpu = Tensor.of(tokenizer.stopTokens());

            Tensor tokens = tokensCpu.to(model.device());
            Tensor eosReached = eosReachedCpu.to(model.device());
            Tensor inputTextMask = inputTextMaskCpu.to(model.device());
            Tensor stopTokens = stopTokensCpu.to(model.device());
            tokensCpu.close();
            eosReachedCpu.close();
            inputTextMaskCpu.close();
            stopTokensCpu.close();

            int prevPos = prefixLen;
            if (promptLen == totalLen && prevPos < totalLen) {
                try (var span = Index.slice(prevPos, totalLen);
                     var window = tokens.get(Index.Colon, span);
                     var logits = model.forward(window, prevPos)) {
                    if (logprobs) {
                        try (var transposed = logits.transpose(1, 2);
                             var entropy = Tensor.crossEntropy(transposed, tokens, "none", pad).neg_()) {
                            tokenLogprobs.close();
                            tokenLogprobs = entropy.detach();
                        }
                    }
                }
            }

            int chunkPos = promptLen;
            for (int curPos = promptLen; curPos < totalLen; curPos++) {
                AutoScope loopScope = new AutoScope();
                Tensor.push(loopScope);
                try {
                    Tensor logits;
                    try (var span = Index.slice(prevPos, curPos);
                         var window = tokens.get(Index.Colon, span)) {
                        logits = model.forward(window, prevPos);
                    }
                    loopScope.add(logits);

                    Tensor nextToken;
                    try (var last = Index.of(-1);
                         var tail = logits.get(Index.Colon, last)) {
                        nextToken = smile.llm.engine.Sampling.sampleNext(tail, temperature, topp);
                    }

                    try (var cur = Index.of(curPos);
                         var textMask = inputTextMask.get(Index.Colon, cur);
                         var currentTokens = tokens.get(Index.Colon, cur);
                         var merged = smile.llm.engine.Sampling.mergeWithPromptMask(
                                 textMask, currentTokens, nextToken)) {
                        nextToken.close();
                        nextToken = merged.detach();
                        tokens.put_(nextToken, Index.Colon, cur);
                    }

                    if (logprobs) {
                        try (var targetSpan = Index.slice(prevPos + 1, curPos + 1);
                             var targets = tokens.get(Index.Colon, targetSpan);
                             var transposed = logits.transpose(1, 2);
                             var entropy = Tensor.crossEntropy(transposed, targets, "none", pad).neg_();
                             var outSpan = Index.slice(prevPos + 1, curPos + 1)) {
                            tokenLogprobs.put_(entropy, Index.Colon, outSpan);
                        }
                    }

                    try (var cur = Index.of(curPos);
                         var text = inputTextMask.get(Index.Colon, cur).not();
                         var stop = nextToken.isin(stopTokens);
                         var textAndStop = text.and(stop)) {
                        eosReached.or_(textAndStop);
                    }

                    nextToken.close();
                    prevPos = curPos;
                    if (listener != null) {
                        listener.onGeneratedTokens(1);
                    }
                } finally {
                    Tensor.pop();
                }

                boolean eos = eosReached.all();
                if (listener != null
                        && (curPos - chunkPos >= 20 || curPos == totalLen - 1 || eos)) {
                    int end = eos ? curPos : curPos + 1;
                    if (end > chunkPos) {
                        long[] longArray;
                        try (var row = Index.of(0);
                             var span = Index.slice(chunkPos, end);
                             var chunkTokens = tokens.get(row, span);
                             var cpuTokens = chunkTokens.to(Device.CPU())) {
                            longArray = cpuTokens.longArray();
                        }
                        var completion = Arrays.stream(longArray).mapToInt(x -> (int) x).toArray();
                        try {
                            // Skip special tokens so chat headers/eot are not shown as text.
                            var chunk = tokenizer.tryDecode(completion, true);
                            chunkPos = end; // advance only after a successful UTF-8 decode
                            if (!chunk.isEmpty()) {
                                listener.onText(chunk);
                            }
                        } catch (java.nio.charset.CharacterCodingException ex) {
                            // Incomplete multibyte sequence at chunk boundary — wait for more tokens.
                            logger.debug("Cannot decode a chunk", ex);
                        }
                    }
                }

                if (eos) break;
            }

            long[] longArray;
            try (var cpuTokens = tokens.to(Device.CPU())) {
                longArray = cpuTokens.longArray();
            }
            float[] logprobArray = null;
            if (logprobs) {
                try (var cpuLogprobs = tokenLogprobs.to(Device.CPU())) {
                    logprobArray = cpuLogprobs.floatArray();
                }
            }

            // Cut to max gen len.
            int start = promptLen;
            var completion = Arrays.stream(longArray)
                    .skip(start)
                    .mapToInt(x -> (int) x)
                    .limit(maxGenLen)
                    .toArray();

            float[] probs = null;
            if (logprobs) {
                probs = Arrays.copyOfRange(logprobArray, start, start + maxGenLen);
            }

            // Cut to after eos tok if any.
            boolean stop = false;
            for (var stopToken : tokenizer.stopTokens()) {
                for (int eosIdx = 0; eosIdx < completion.length; eosIdx++) {
                    if (completion[eosIdx] == stopToken) {
                        stop = true;
                        completion = Arrays.copyOf(completion, eosIdx);
                        if (logprobs) {
                            probs = Arrays.copyOf(probs, eosIdx);
                        }
                        break;
                    }
                }
            }

            var reason = stop ? FinishReason.stop : FinishReason.length;
            ChatCompletion prediction = new ChatCompletion(
                    name, tokenizer.decode(completion), prompt, completion, reason, probs);
            model.kvCachePool().finishRequest(concatTokens(prompt, completion));
            return prediction;
            } finally {
                Tensor.pop();
            }
        } finally {
            // finishRequest clears the binding; otherwise release private pages.
            model.kvCachePool().unbindRequests();
            model.device().emptyCache();
        }
    }

    /** Concatenates prompt and completion token ids for radix insert. */
    private static int[] concatTokens(int[] prompt, int[] completion) {
        int[] seq = new int[prompt.length + completion.length];
        System.arraycopy(prompt, 0, seq, 0, prompt.length);
        System.arraycopy(completion, 0, seq, prompt.length, completion.length);
        return seq;
    }

    /**
     * Performs text completion for a prompt.
     *
     * @param prompt      text prompt.
     * @param maxGenLen   maximum number of new tokens to generate.
     * @param temperature temperature value for controlling randomness in sampling.
     * @param topp        top-p probability threshold for nucleus sampling.
     * @param logprobs    flag indicating whether to compute token log probabilities.
     * @param seed        optional RNG seed to sample deterministically.
     * @param listener    optional generation progress callback.
     * @return the generated text completion.
     */
    public ChatCompletion complete(String prompt, int maxGenLen, double temperature, double topp,
                                   boolean logprobs, long seed, GenerationListener listener) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        return generate(tokenizer.encode(prompt, true, false),
                maxGenLen, temperature, topp, logprobs, seed, listener);
    }

    /**
     * Generates an assistant response for a conversational dialog.
     *
     * @param dialog      ordered conversation turns.
     * @param maxGenLen   maximum number of new tokens to generate.
     * @param temperature temperature value for controlling randomness in sampling.
     * @param topp        top-p probability threshold for nucleus sampling.
     * @param logprobs    flag indicating whether to compute token log probabilities.
     * @param seed        optional RNG seed to sample deterministically.
     * @param listener    optional generation progress callback.
     * @return the generated chat response.
     */
    @Override
    public ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature, double topp,
                               boolean logprobs, long seed, GenerationListener listener) {
        if (dialog == null) {
            throw new IllegalArgumentException("dialog must not be null");
        }
        return generate(tokenizer.encodeDialog(dialog),
                maxGenLen, temperature, topp, logprobs, seed, listener);
    }

    @Override
    public LanguageModel model() {
        return this;
    }

    @Override
    public smile.llm.cache.KvCachePool kvCachePool() {
        return model.kvCachePool();
    }

    @Override
    public int padToken() {
        return tokenizer.pad();
    }

    @Override
    public int[] stopTokens() {
        return tokenizer.stopTokens();
    }

    @Override
    public String decode(int[] tokens) {
        return tokenizer.decode(tokens);
    }

    @Override
    public String tryDecode(int[] tokens, boolean skipSpecial)
            throws java.nio.charset.CharacterCodingException {
        return tokenizer.tryDecode(tokens, skipSpecial);
    }
}
