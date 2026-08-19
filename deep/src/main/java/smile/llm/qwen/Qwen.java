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
package smile.llm.qwen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SubmissionPublisher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.cache.KvCachePool;
import smile.llm.llama.Llama;
import smile.llm.parallel.ParallelConfig;
import smile.llm.parallel.ParallelState;
import smile.llm.parallel.TensorParallelGroup;
import smile.llm.parallel.TensorShardSpec;
import smile.torch.smile_torch_h;
import smile.util.AutoScope;

/**
 * Qwen3.5 hybrid text model (Gated DeltaNet + gated full attention).
 *
 * @author Haifeng Li
 */
public class Qwen implements LanguageModel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Qwen.class);

    static final String family = "alibaba/qwen3.5";

    private static final Pattern HF_LAYER_WEIGHT = Pattern.compile(
            "^(?:language_model\\.)?model\\.layers\\.(\\d+)\\.(self_attn|linear_attn|mlp|input_layernorm|post_attention_layernorm)\\.(.+)$");

    final String name;
    /** Rank-0 model (also {@code models[0]}). */
    final QwenModel model;
    /** One shard per TP rank; length 1 when tensor-parallel size is 1. */
    final QwenModel[] models;
    final TensorParallelGroup tpGroup;
    final Tokenizer tokenizer;
    final QwenModelArgs params;

    /**
     * Constructor.
     */
    public Qwen(String name, QwenModel model, Tokenizer tokenizer, QwenModelArgs params) {
        this(name, new QwenModel[]{model}, null, tokenizer, params);
    }

    /**
     * Tensor-parallel constructor.
     */
    public Qwen(String name, QwenModel[] models, TensorParallelGroup tpGroup,
                Tokenizer tokenizer, QwenModelArgs params) {
        if (models == null || models.length < 1) {
            throw new IllegalArgumentException("models required");
        }
        this.name = name;
        this.models = models;
        this.model = models[0];
        this.tpGroup = tpGroup;
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

    @Override
    public String name() {
        return name;
    }

    /** Hyperparameters from the checkpoint. */
    public QwenModelArgs params() {
        return params;
    }

    /**
     * Builds a Qwen instance from a HuggingFace checkpoint directory.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId)
            throws IOException {
        return build(checkpointDir, maxBatchSize, maxSeqLen, deviceId, 0, null, ParallelConfig.single(deviceId));
    }

    /**
     * Builds a Qwen instance from a HuggingFace checkpoint directory.
     *
     * @param memFractionStatic fraction of free GPU memory for the KV pool; {@code <=0} keeps test sizing.
     * @param kvCacheDtype      optional KV dtype override.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId,
                             double memFractionStatic, String kvCacheDtype) throws IOException {
        return build(checkpointDir, maxBatchSize, maxSeqLen, deviceId, memFractionStatic, kvCacheDtype,
                ParallelConfig.single(deviceId));
    }

    /**
     * Builds a Qwen instance with optional tensor parallelism.
     *
     * @param parallel {@link ParallelConfig#tensorParallel} for multi-GPU; {@code ppSize} must be 1.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId,
                             double memFractionStatic, String kvCacheDtype,
                             ParallelConfig parallel) throws IOException {
        File dir = new File(checkpointDir);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Checkpoint directory not found: " + checkpointDir);
        }
        if (parallel == null) {
            parallel = ParallelConfig.single(deviceId);
        }
        final ParallelConfig parallelConfig = parallel;

        boolean cuda = deviceId >= 0 || (parallelConfig.isTensorParallel() && parallelConfig.devices()[0] >= 0);
        ScalarType computeDtype = ScalarType.Float;
        if (cuda) {
            var startTime = System.currentTimeMillis();
            Device.CUDA(parallelConfig.devices()[0]); // touch primary device
            computeDtype = Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half;
            smile_torch_h.smile_set_default_dtype(computeDtype.code());
            var time = System.currentTimeMillis() - startTime;
            logger.info("Initialized CUDA (tpSize={}): {}.{} seconds",
                    parallelConfig.tpSize(), time / 1000, time % 1000);
        }

        var startTime = System.currentTimeMillis();
        Path configJson = Path.of(checkpointDir, "config.json");
        if (!Files.exists(configJson)) {
            throw new IllegalArgumentException("config.json not found in " + checkpointDir);
        }

        QwenModelArgs modelArgs = QwenModelArgs.fromHuggingFace(configJson.toString(), maxBatchSize, maxSeqLen);
        ScalarType cacheDtype = resolveKvCacheDtype(kvCacheDtype, configJson, computeDtype);
        logger.info("KV cache dtype: {} (override={}, compute={})", cacheDtype, kvCacheDtype, computeDtype);

        Tokenizer tokenizer = Tokenizer.of(checkpointDir);
        tokenizer.requireChatSpecialsInVocab(modelArgs.vocabSize());
        // HF often pads embedding/lm_head (e.g. Qwen3.5: 248320 padded) above the
        // highest tokenizer id; that is expected. Warn only if the tokenizer can
        // emit ids the embedding table cannot hold.
        if (tokenizer.size() > modelArgs.vocabSize()) {
            logger.warn("Tokenizer size {} exceeds config vocab_size {}; embedding gather may OOB",
                    tokenizer.size(), modelArgs.vocabSize());
        } else if (tokenizer.size() > 0 && tokenizer.size() < modelArgs.vocabSize()) {
            logger.info("Tokenizer size {} < config vocab_size {} (HF padded embedding)",
                    tokenizer.size(), modelArgs.vocabSize());
        }

        TensorParallelGroup tpGroup = new TensorParallelGroup(parallelConfig);
        Map<String, String> weightMap = readWeightMap(dir);
        QwenModel[] models = new QwenModel[parallelConfig.tpSize()];

        if (parallelConfig.tpSize() == 1) {
            models[0] = buildRank(0, parallelConfig, modelArgs, cuda, memFractionStatic, cacheDtype,
                    dir, weightMap, tpGroup);
        } else {
            // Each TP rank targets a different GPU; build + shard-load concurrently.
            // Do not call Tensor.setDefaultOptions here — it is process-global and
            // racy across threads. Layers allocate on CPU, then model.to(device).
            ExecutorService pool = Executors.newFixedThreadPool(parallelConfig.tpSize());
            try {
                List<Future<QwenModel>> futures = new ArrayList<>(parallelConfig.tpSize());
                for (int r = 0; r < parallelConfig.tpSize(); r++) {
                    final int rank = r;
                    futures.add(pool.submit(() -> buildRank(rank, parallelConfig, modelArgs, cuda,
                            memFractionStatic, cacheDtype, dir, weightMap, tpGroup)));
                }
                for (int r = 0; r < parallelConfig.tpSize(); r++) {
                    models[r] = futures.get(r).get();
                }
            } catch (Exception e) {
                throw new IOException("Parallel TP rank load failed", e);
            } finally {
                pool.shutdownNow();
            }
        }

        var time = System.currentTimeMillis() - startTime;
        logger.info("Model {}: loaded in {}.{} seconds (tpSize={})",
                checkpointDir, time / 1000, time % 1000, parallelConfig.tpSize());
        return new Qwen(dir.getName(), models, tpGroup, tokenizer, modelArgs);
    }

    /**
     * Builds one TP rank: construct module tree, load/shard HF weights, size KV cache.
     */
    private static QwenModel buildRank(int rank, ParallelConfig parallel, QwenModelArgs modelArgs,
                                       boolean cuda, double memFractionStatic, ScalarType cacheDtype,
                                       File dir, Map<String, String> weightMap,
                                       TensorParallelGroup tpGroup) throws IOException {
        Device device = cuda ? Device.CUDA(parallel.devices()[rank]) : Device.CPU();
        TensorShardSpec shard = TensorShardSpec.forRank(
                parallel.tpSize(), rank,
                modelArgs.numHeads(), modelArgs.numKvHeads(), modelArgs.intermediateSize(),
                modelArgs.linearNumKeyHeads(), modelArgs.linearNumValueHeads());

        KvCachePool bootstrap = null;
        if (modelArgs.numFullAttentionLayers() > 0) {
            var layout = modelArgs.kvCacheLayout(shard);
            bootstrap = memFractionStatic > 0
                    ? KvCachePool.bootstrap(layout)
                    : KvCachePool.forTesting(layout, device);
        }
        DeltaNetStatePool statePool = null;
        if (modelArgs.numLinearAttentionLayers() > 0) {
            statePool = new DeltaNetStatePool(
                    modelArgs.numLinearAttentionLayers(),
                    shard.linearNumValueHeads(),
                    modelArgs.linearKeyHeadDim(),
                    modelArgs.linearValueHeadDim(),
                    modelArgs.linearConvDim(shard),
                    modelArgs.linearConvKernelDim(),
                    modelArgs.maxBatchSize(),
                    memFractionStatic > 0 ? Device.CPU() : device,
                    ScalarType.Float);
        }

        QwenModel model = new QwenModel(modelArgs, bootstrap, statePool, device, shard, tpGroup);
        loadHuggingFaceWeights(model, dir, Device.CPU(), shard, weightMap);
        model.eval();

        // Allocate fixed DeltaNet state before the KV pool so memFractionStatic
        // is measured against free memory after those buffers exist.
        if (memFractionStatic > 0 && modelArgs.numLinearAttentionLayers() > 0) {
            var gpuState = new DeltaNetStatePool(
                    modelArgs.numLinearAttentionLayers(),
                    shard.linearNumValueHeads(),
                    modelArgs.linearKeyHeadDim(),
                    modelArgs.linearValueHeadDim(),
                    modelArgs.linearConvDim(shard),
                    modelArgs.linearConvKernelDim(),
                    modelArgs.maxBatchSize(),
                    device,
                    cacheDtype);
            var previous = model.deltaNetStatePool;
            model.deltaNetStatePool = gpuState;
            for (var layer : model.layers) {
                if (layer.linearAttn != null) {
                    layer.linearAttn.setStatePool(gpuState);
                }
            }
            if (previous != null) previous.close();
        }
        if (memFractionStatic > 0 && modelArgs.numFullAttentionLayers() > 0) {
            model.kvCachePool().close();
            device.emptyCache();
            var pool = KvCachePool.allocate(
                    modelArgs.kvCacheLayout(shard), device, cacheDtype, memFractionStatic);
            model.setKvCachePool(pool, false);
        }
        return model;
    }

    static ScalarType resolveKvCacheDtype(String override, Path configJson, ScalarType fallback)
            throws IOException {
        if (override != null && !override.isBlank()) {
            return Llama.parseDtypeName(override);
        }
        if (Files.exists(configJson)) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(configJson.toFile());
            JsonNode text = root.has("text_config") ? root.get("text_config") : root;
            if (text.has("torch_dtype") && !text.get("torch_dtype").asString().isBlank()) {
                return Llama.parseDtypeName(text.get("torch_dtype").asString());
            }
            if (root.has("torch_dtype") && !root.get("torch_dtype").asString().isBlank()) {
                return Llama.parseDtypeName(root.get("torch_dtype").asString());
            }
        }
        return fallback;
    }

    private static void loadHuggingFaceWeights(QwenModel model, File dir, Device loadDevice,
                                               TensorShardSpec shard,
                                               Map<String, String> weightMap) throws IOException {
        Device target = model.device();
        Map<String, List<String>> shardToKeys = new LinkedHashMap<>();
        for (var entry : weightMap.entrySet()) {
            shardToKeys.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        Set<String> loaded = new HashSet<>();
        for (var shardEntry : shardToKeys.entrySet()) {
            String shardFile = shardEntry.getKey();
            Path shardPath = Path.of(dir.getPath(), shardFile);
            logger.info("Loading safetensors shard: {} (tpRank={})", shardFile,
                    shard != null ? shard.tpRank() : 0);
            // Load onto CPU first so large tensors can be sliced before hitting GPU memory.
            SafeTensors st = SafeTensors.read(shardPath.toString(), loadDevice, shardEntry.getValue());
            try {
                Map<String, Tensor> stateDict = new HashMap<>();
                List<Tensor> owned = new ArrayList<>();
                try {
                    for (String hfName : shardEntry.getValue()) {
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
                        if (smileName.contains("linear_attn.conv1d.weight") && src.dim() == 3) {
                            value = src.reshape(src.shape()[0], src.shape()[2]);
                            owned.add(value);
                        }
                        Tensor sliced = QwenWeightShard.shard(smileName, value, model.params(), shard);
                        if (sliced != value && sliced != src) {
                            owned.add(sliced);
                        }
                        Tensor onDevice = sliced.to(target);
                        if (onDevice != sliced) {
                            owned.add(onDevice);
                        }
                        // Materialize a contiguous clone so state-dict storage outlives views.
                        Tensor contiguous = onDevice.contiguous();
                        if (contiguous != onDevice) {
                            owned.add(contiguous);
                        }
                        stateDict.put(smileName, contiguous);
                        loaded.add(smileName);
                    }

                    if (!weightMap.containsKey("lm_head.weight")
                            && !weightMap.containsKey("language_model.lm_head.weight")
                            && !loaded.contains("lm_head.weight")) {
                        for (String embKey : List.of(
                                "model.embed_tokens.weight",
                                "language_model.model.embed_tokens.weight")) {
                            if (st.tensors().containsKey(embKey)) {
                                Tensor onDevice = st.tensors().get(embKey).to(target);
                                owned.add(onDevice);
                                Tensor emb = onDevice.contiguous();
                                if (emb != onDevice) {
                                    owned.add(emb);
                                }
                                stateDict.put("lm_head.weight", emb);
                                loaded.add("lm_head.weight");
                                break;
                            }
                        }
                    }

                    model.loadStateDict(stateDict, false);
                } finally {
                    for (Tensor t : owned) {
                        t.close();
                    }
                }
            } finally {
                for (Tensor t : st.tensors().values()) {
                    t.close();
                }
            }
        }
        logger.info("Loaded {} parameters from HuggingFace safetensors (tpRank={})",
                loaded.size(), shard != null ? shard.tpRank() : 0);
    }

    static Map<String, String> readWeightMap(File dir) throws IOException {
        Path indexPath = Path.of(dir.getPath(), "model.safetensors.index.json");
        Map<String, String> map = new LinkedHashMap<>();
        if (Files.exists(indexPath)) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(indexPath.toFile());
            JsonNode weightMap = root.get("weight_map");
            weightMap.properties().forEach(e -> map.put(e.getKey(), e.getValue().asString()));
            return map;
        }
        List<String> shards = getSafeTensorFiles(dir);
        if (shards.isEmpty()) {
            throw new IOException("No safetensors files found in " + dir);
        }
        for (String shard : shards) {
            for (String name : SafeTensors.listTensors(Path.of(dir.getPath(), shard).toString())) {
                map.put(name, shard);
            }
        }
        return map;
    }

    private static List<String> getSafeTensorFiles(File dir) {
        List<String> files = new ArrayList<>();
        File[] listed = dir.listFiles();
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
     * Maps a HuggingFace parameter name onto the registered SMILE module path.
     * Returns {@code null} for vision / MTP / unrecognized tensors.
     */
    static String remapHuggingFaceName(String hfName) {
        if (hfName.startsWith("visual.") || hfName.startsWith("mtp.")
                || hfName.startsWith("vision_")) {
            return null;
        }

        String name = hfName;
        if (name.startsWith("language_model.")) {
            name = name.substring("language_model.".length());
        }

        if (name.equals("model.embed_tokens.weight")) {
            return "embed_tokens.weight";
        }
        if (name.equals("model.norm.weight")) {
            return "norm.weight";
        }
        if (name.equals("lm_head.weight")) {
            return "lm_head.weight";
        }

        Matcher m = HF_LAYER_WEIGHT.matcher(hfName);
        if (!m.matches()) {
            // Try without language_model prefix already stripped above
            m = Pattern.compile(
                    "^model\\.layers\\.(\\d+)\\.(self_attn|linear_attn|mlp|input_layernorm|post_attention_layernorm)\\.(.+)$")
                    .matcher(name);
            if (!m.matches()) {
                return null;
            }
        }
        String layer = m.group(1);
        String component = m.group(2);
        String rest = m.group(3);
        String prefix = "layers." + layer + ".";

        return switch (component) {
            case "self_attn" -> prefix + "self_attn." + rest;
            case "linear_attn" -> {
                if (rest.equals("conv1d.weight")) {
                    yield prefix + "linear_attn.conv1d.weight";
                }
                yield prefix + "linear_attn." + rest;
            }
            case "mlp" -> switch (rest) {
                case "gate_proj.weight" -> prefix + "mlp.w1.weight";
                case "down_proj.weight" -> prefix + "mlp.w2.weight";
                case "up_proj.weight" -> prefix + "mlp.w3.weight";
                default -> null;
            };
            case "input_layernorm" -> prefix + "input_layernorm." + rest;
            case "post_attention_layernorm" -> prefix + "post_attention_layernorm." + rest;
            default -> null;
        };
    }

    @Override
    public ChatCompletion[] generate(int[][] prompts, int maxGenLen, double temperature,
                                     double topp, boolean logprobs, long seed,
                                     SubmissionPublisher<String> publisher) {
        int batchSize = prompts.length;
        if (batchSize > params.maxBatchSize()) {
            throw new IllegalArgumentException("The number of prompts is greater than max_batch_size");
        }
        if (publisher != null && batchSize > 1) {
            throw new IllegalArgumentException("The batch size is > 1 while publisher is provided");
        }

        int minPromptLen = Integer.MAX_VALUE;
        int maxPromptLen = Integer.MIN_VALUE;
        int vocabSize = params.vocabSize();
        for (var prompt : prompts) {
            minPromptLen = Math.min(minPromptLen, prompt.length);
            maxPromptLen = Math.max(maxPromptLen, prompt.length);
            for (int token : prompt) {
                if (token < 0 || token >= vocabSize) {
                    throw new IllegalArgumentException(
                            "Prompt token id " + token + " out of range for vocab_size "
                                    + vocabSize + " (im_start="
                                    + tokenizer.specialToken("<|im_start|>")
                                    + ", im_end=" + tokenizer.specialToken("<|im_end|>")
                                    + "). This causes CUDA embedding gather OOB.");
                }
            }
        }
        if (maxPromptLen > params.maxSeqLen()) {
            throw new IllegalArgumentException("The prompt length is greater than max_seq_len");
        }

        if (seed != 0) {
            smile_torch_h.smile_manual_seed(seed);
        }

        try (var guard = Tensor.noGradGuard();
             var scope = new AutoScope()) {
            Tensor.push(scope);
            try {
            int totalLen = Math.min(params.maxSeqLen(), maxGenLen + maxPromptLen);

            if (model.kvCachePool() != null) {
                for (QwenModel m : models) {
                    if (m.kvCachePool() != null) {
                        m.kvCachePool().bindRequests(batchSize, totalLen);
                    }
                }
            }
            if (model.deltaNetStatePool() != null) {
                for (QwenModel m : models) {
                    if (m.deltaNetStatePool() != null) {
                        m.deltaNetStatePool().reset(batchSize);
                    }
                }
            }

            int pad = tokenizer.pad();
            var cpuOpts = new Tensor.Options()
                    .device(Device.CPU())
                    .dtype(ScalarType.Int64)
                    .requireGradients(false);
            Tensor tokensCpu = Tensor.zeros(cpuOpts, batchSize, totalLen).fill_(pad);
            for (int i = 0; i < batchSize; i++) {
                try (var prompt = Tensor.of(prompts[i]);
                     var row = Index.of(i);
                     var span = Index.slice(0, prompts[i].length)) {
                    tokensCpu.put_(prompt, row, span);
                }
            }

            Tensor tokenLogprobs = null;
            if (logprobs) {
                var opts = new Tensor.Options().device(model.device()).requireGradients(false).dtype(ScalarType.Float);
                tokenLogprobs = Tensor.zeros(opts, batchSize, totalLen);
            }

            Tensor eosReached = Tensor.of(new boolean[batchSize]);
            Tensor inputTextMask = tokensCpu.ne(pad);
            Tensor stopTokens = Tensor.of(tokenizer.stopTokens());

            Tensor[] tokens = new Tensor[models.length];
            Tensor[] eos = new Tensor[models.length];
            Tensor[] masks = new Tensor[models.length];
            Tensor[] stops = new Tensor[models.length];
            for (int r = 0; r < models.length; r++) {
                Device d = models[r].device();
                tokens[r] = tokensCpu.to(d);
                eos[r] = eosReached.to(d);
                masks[r] = inputTextMask.to(d);
                stops[r] = stopTokens.to(d);
            }
            tokensCpu.close();
            eosReached.close();
            inputTextMask.close();
            stopTokens.close();

            int prevPos = 0;
            int chunkPos = minPromptLen;
            ExecutorService pool = models.length > 1
                    ? Executors.newFixedThreadPool(models.length)
                    : null;
            try {
            for (int curPos = minPromptLen; curPos < totalLen; curPos++) {
                AutoScope loopScope = new AutoScope();
                Tensor.push(loopScope);
                Tensor[] logits = null;
                try {
                    logits = forwardAll(tokens, prevPos, curPos, pool);
                    for (Tensor l : logits) {
                        loopScope.add(l);
                    }

                    Tensor nextToken;
                    try (var last = Index.of(-1);
                         var tail = logits[0].get(Index.Colon, last)) {
                        if (temperature > 0) {
                            try (var probs = tail.div(temperature).softmax(-1)) {
                                nextToken = probs.topp(topp);
                            }
                        } else {
                            nextToken = tail.argmax(-1, false);
                        }
                    }

                    nextToken = nextToken.reshape(-1);
                    try (var cur = Index.of(curPos);
                         var textMask = masks[0].get(Index.Colon, cur);
                         var currentTokens = tokens[0].get(Index.Colon, cur);
                         var merged = Tensor.where(textMask, currentTokens, nextToken)) {
                        nextToken.close();
                        nextToken = merged.detach();
                        for (int r = 0; r < models.length; r++) {
                            Tensor local = r == 0 ? nextToken : nextToken.to(models[r].device());
                            tokens[r].put_(local, Index.Colon, cur);
                            if (r != 0) local.close();
                        }
                    }

                    if (logprobs) {
                        try (var targetSpan = Index.slice(prevPos + 1, curPos + 1);
                             var targets = tokens[0].get(Index.Colon, targetSpan);
                             var transposed = logits[0].transpose(1, 2);
                             var entropy = Tensor.crossEntropy(transposed, targets, "none", pad).neg_();
                             var outSpan = Index.slice(prevPos + 1, curPos + 1)) {
                            tokenLogprobs.put_(entropy, Index.Colon, outSpan);
                        }
                    }

                    try (var cur = Index.of(curPos);
                         var text = masks[0].get(Index.Colon, cur).not();
                         var stop = nextToken.isin(stops[0]);
                         var textAndStop = text.and(stop)) {
                        eos[0].or_(textAndStop);
                        for (int r = 1; r < models.length; r++) {
                            try (Tensor e = eos[0].to(models[r].device())) {
                                smile.torch.Native.copy_(eos[r], e);
                            }
                        }
                    }

                    nextToken.close();
                    prevPos = curPos;
                } finally {
                    Tensor.pop();
                }

                boolean done = eos[0].all();
                if (publisher != null && (curPos - chunkPos >= 20 || curPos == totalLen - 1 || done)) {
                    int end = done ? curPos : curPos + 1;
                    if (end > chunkPos) {
                        long[] longArray;
                        try (var row = Index.of(0);
                             var span = Index.slice(chunkPos, end);
                             var chunkTokens = tokens[0].get(row, span);
                             var cpuTokens = chunkTokens.to(Device.CPU())) {
                            longArray = cpuTokens.longArray();
                        }
                        var completion = Arrays.stream(longArray).mapToInt(x -> (int) x).toArray();
                        try {
                            var chunk = tokenizer.tryDecode(completion, true);
                            chunkPos = end;
                            if (!chunk.isEmpty()) {
                                publisher.submit(chunk);
                            }
                        } catch (java.nio.charset.CharacterCodingException ex) {
                            logger.debug("Cannot decode a chunk", ex);
                        }
                    }
                }
                if (done) break;
            }
            } finally {
                if (pool != null) pool.shutdownNow();
            }

            long[] longArray;
            try (var cpuTokens = tokens[0].to(Device.CPU())) {
                longArray = cpuTokens.longArray();
            }
            float[] logprobArray = null;
            if (logprobs) {
                try (var cpuLogprobs = tokenLogprobs.to(Device.CPU())) {
                    logprobArray = cpuLogprobs.floatArray();
                }
            }
            ChatCompletion[] predictions = new ChatCompletion[batchSize];
            for (int i = 0; i < batchSize; i++) {
                int start = prompts[i].length;
                var completion = Arrays.stream(longArray)
                        .skip((long) i * totalLen + start)
                        .mapToInt(x -> (int) x)
                        .limit(prompts[i].length + maxGenLen - start)
                        .toArray();

                float[] probs = null;
                if (logprobs) {
                    probs = Arrays.copyOfRange(logprobArray, i * totalLen + start,
                            i * totalLen + prompts[i].length + maxGenLen);
                }

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
                predictions[i] = new ChatCompletion(name, tokenizer.decode(completion),
                        prompts[i], completion, reason, probs);
            }

            if (publisher != null) publisher.close();
            return predictions;
            } finally {
                Tensor.pop();
            }
        } finally {
            for (QwenModel m : models) {
                if (m.kvCachePool() != null) {
                    m.kvCachePool().unbindRequests();
                }
                if (m.deltaNetStatePool() != null) {
                    m.deltaNetStatePool().unbind();
                }
                m.device().emptyCache();
            }
        }
    }

    /**
     * Runs {@link QwenModel#forward} on every TP rank (in parallel when {@code tpSize > 1}).
     */
    private Tensor[] forwardAll(Tensor[] tokens, int prevPos, int curPos, ExecutorService pool) {
        Tensor[] logits = new Tensor[models.length];
        if (models.length == 1) {
            try (var span = Index.slice(prevPos, curPos);
                 var window = tokens[0].get(Index.Colon, span)) {
                logits[0] = models[0].forward(window, prevPos);
            }
            return logits;
        }
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int r = 0; r < models.length; r++) {
            final int rank = r;
            futures.add(pool.submit(() -> {
                ParallelState.setCurrent(tpGroup.state(rank));
                try (var span = Index.slice(prevPos, curPos);
                     var window = tokens[rank].get(Index.Colon, span)) {
                    return models[rank].forward(window, prevPos);
                } finally {
                    ParallelState.clearCurrent();
                }
            }));
        }
        try {
            for (int r = 0; r < models.length; r++) {
                logits[r] = futures.get(r).get();
            }
        } catch (Exception e) {
            for (Tensor l : logits) {
                if (l != null) {
                    l.close();
                }
            }
            throw new RuntimeException("TP forward failed", e);
        }
        return logits;
    }

    /**
     * Text completion from string prompts.
     */
    public ChatCompletion[] complete(String[] prompts, int maxGenLen, double temperature, double topp,
                                     boolean logprobs, long seed, SubmissionPublisher<String> publisher) {
        int batchSize = prompts.length;
        int[][] tokens = new int[batchSize][];
        for (int i = 0; i < batchSize; i++) {
            tokens[i] = tokenizer.encode(prompts[i], false, false);
        }
        return generate(tokens, maxGenLen, temperature, topp, logprobs, seed, publisher);
    }

    @Override
    public ChatCompletion[] chat(Message[][] dialogs, int maxGenLen, double temperature, double topp,
                                 boolean logprobs, long seed, SubmissionPublisher<String> publisher) {
        int batchSize = dialogs.length;
        int[][] tokens = new int[batchSize][];
        for (int i = 0; i < batchSize; i++) {
            tokens[i] = tokenizer.encodeDialog(dialogs[i]);
        }
        return generate(tokens, maxGenLen, temperature, topp, logprobs, seed, publisher);
    }
}
