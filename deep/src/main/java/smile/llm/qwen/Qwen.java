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
    final QwenModel model;
    final Tokenizer tokenizer;
    final QwenModelArgs params;

    /**
     * Constructor.
     */
    public Qwen(String name, QwenModel model, Tokenizer tokenizer, QwenModelArgs params) {
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
        return build(checkpointDir, maxBatchSize, maxSeqLen, deviceId, 0, null);
    }

    /**
     * Builds a Qwen instance from a HuggingFace checkpoint directory.
     *
     * @param memFractionStatic fraction of free GPU memory for the KV pool; {@code <=0} keeps test sizing.
     * @param kvCacheDtype      optional KV dtype override.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId,
                             double memFractionStatic, String kvCacheDtype) throws IOException {
        File dir = new File(checkpointDir);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Checkpoint directory not found: " + checkpointDir);
        }

        Device device = Device.CPU();
        ScalarType computeDtype = ScalarType.Float;
        if (deviceId >= 0) {
            var startTime = System.currentTimeMillis();
            device = Device.CUDA(deviceId);
            computeDtype = Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half;
            smile_torch_h.smile_set_default_dtype(computeDtype.code());
            var time = System.currentTimeMillis() - startTime;
            logger.info("Initialized CUDA[{}]: {}.{} seconds", deviceId, time / 1000, time % 1000);
        }

        var options = new Tensor.Options().device(device).requireGradients(false);
        Tensor.setDefaultOptions(options);

        var startTime = System.currentTimeMillis();
        Path configJson = Path.of(checkpointDir, "config.json");
        if (!Files.exists(configJson)) {
            throw new IllegalArgumentException("config.json not found in " + checkpointDir);
        }

        QwenModelArgs modelArgs = QwenModelArgs.fromHuggingFace(configJson.toString(), maxBatchSize, maxSeqLen);
        ScalarType cacheDtype = resolveKvCacheDtype(kvCacheDtype, configJson, computeDtype);
        logger.info("KV cache dtype: {} (override={}, compute={})", cacheDtype, kvCacheDtype, computeDtype);

        Tokenizer tokenizer = Tokenizer.of(checkpointDir);
        if (tokenizer.size() > 0 && tokenizer.size() != modelArgs.vocabSize()) {
            logger.warn("Tokenizer size {} != config vocab_size {}", tokenizer.size(), modelArgs.vocabSize());
        }

        KvCachePool bootstrap = null;
        if (modelArgs.numFullAttentionLayers() > 0) {
            bootstrap = memFractionStatic > 0
                    ? KvCachePool.bootstrap(modelArgs.kvCacheLayout())
                    : KvCachePool.forTesting(modelArgs.kvCacheLayout(), device);
        }
        DeltaNetStatePool statePool = null;
        if (modelArgs.numLinearAttentionLayers() > 0) {
            statePool = new DeltaNetStatePool(
                    modelArgs.numLinearAttentionLayers(),
                    modelArgs.linearNumValueHeads(),
                    modelArgs.linearKeyHeadDim(),
                    modelArgs.linearValueHeadDim(),
                    modelArgs.linearConvDim(),
                    modelArgs.linearConvKernelDim(),
                    modelArgs.maxBatchSize(),
                    memFractionStatic > 0 ? Device.CPU() : device,
                    ScalarType.Float);
        }

        var model = new QwenModel(modelArgs, bootstrap, statePool, device);
        loadHuggingFaceWeights(model, dir, device);
        model.eval();

        if (memFractionStatic > 0 && modelArgs.numFullAttentionLayers() > 0) {
            model.kvCachePool().close();
            device.emptyCache();
            var pool = KvCachePool.allocate(modelArgs.kvCacheLayout(), device, cacheDtype, memFractionStatic);
            model.setKvCachePool(pool, false);
        }
        if (memFractionStatic > 0 && modelArgs.numLinearAttentionLayers() > 0) {
            // Re-allocate DeltaNet state on the compute device after weight load.
            var gpuState = new DeltaNetStatePool(
                    modelArgs.numLinearAttentionLayers(),
                    modelArgs.linearNumValueHeads(),
                    modelArgs.linearKeyHeadDim(),
                    modelArgs.linearValueHeadDim(),
                    modelArgs.linearConvDim(),
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

        var time = System.currentTimeMillis() - startTime;
        logger.info("Model {}: loaded in {}.{} seconds", checkpointDir, time / 1000, time % 1000);
        return new Qwen(dir.getName(), model, tokenizer, modelArgs);
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

    private static void loadHuggingFaceWeights(QwenModel model, File dir, Device device) throws IOException {
        Map<String, String> weightMap = readWeightMap(dir);
        Map<String, List<String>> shardToKeys = new LinkedHashMap<>();
        for (var entry : weightMap.entrySet()) {
            shardToKeys.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        Set<String> loaded = new HashSet<>();
        for (var shardEntry : shardToKeys.entrySet()) {
            String shardFile = shardEntry.getKey();
            Path shardPath = Path.of(dir.getPath(), shardFile);
            logger.info("Loading safetensors shard: {}", shardFile);
            SafeTensors st = SafeTensors.read(shardPath.toString(), device, shardEntry.getValue());
            try {
                Map<String, Tensor> stateDict = new HashMap<>();
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
                    // Conv1d weight HF shape [C,1,K] → smile [C,K]
                    if (smileName.contains("linear_attn.conv1d.weight") && src.dim() == 3) {
                        value = src.reshape(src.shape()[0], src.shape()[2]);
                    }
                    stateDict.put(smileName, value);
                    loaded.add(smileName);
                }

                if (!weightMap.containsKey("lm_head.weight")
                        && !weightMap.containsKey("language_model.lm_head.weight")
                        && !loaded.contains("lm_head.weight")) {
                    for (String embKey : List.of(
                            "model.embed_tokens.weight",
                            "language_model.model.embed_tokens.weight")) {
                        if (st.tensors().containsKey(embKey)) {
                            stateDict.put("lm_head.weight", st.tensors().get(embKey));
                            loaded.add("lm_head.weight");
                            break;
                        }
                    }
                }

                model.loadStateDict(stateDict, false);
            } finally {
                for (Tensor t : st.tensors().values()) {
                    t.close();
                }
            }
        }
        logger.info("Loaded {} parameters from HuggingFace safetensors", loaded.size());
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
        for (var prompt : prompts) {
            minPromptLen = Math.min(minPromptLen, prompt.length);
            maxPromptLen = Math.max(maxPromptLen, prompt.length);
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
            int totalLen = Math.min(params.maxSeqLen(), maxGenLen + maxPromptLen);

            if (model.kvCachePool() != null) {
                model.kvCachePool().bindRequests(batchSize, totalLen);
            }
            if (model.deltaNetStatePool() != null) {
                model.deltaNetStatePool().reset(batchSize);
            }

            int pad = tokenizer.pad();
            Tensor tokens = Tensor.full(pad, batchSize, totalLen);
            for (int i = 0; i < batchSize; i++) {
                try (var prompt = Tensor.of(prompts[i]);
                     var row = Index.of(i);
                     var span = Index.slice(0, prompts[i].length)) {
                    tokens.put_(prompt, row, span);
                }
            }

            Tensor tokenLogprobs = null;
            if (logprobs) {
                var opts = new Tensor.Options().device(model.device()).requireGradients(false).dtype(ScalarType.Float);
                tokenLogprobs = Tensor.zeros(opts, batchSize, totalLen);
            }

            Tensor eosReached = Tensor.of(new boolean[batchSize]);
            Tensor inputTextMask = tokens.ne(pad);
            Tensor stopTokens = Tensor.of(tokenizer.stopTokens());

            tokens = tokens.to(model.device());
            eosReached = eosReached.to(model.device());
            inputTextMask = inputTextMask.to(model.device());
            stopTokens = stopTokens.to(model.device());

            int prevPos = 0;
            int chunkPos = minPromptLen;
            for (int curPos = minPromptLen; curPos < totalLen; curPos++) {
                try (var loopScope = new AutoScope()) {
                    Tensor.push(loopScope);
                    Tensor logits;
                    try (var span = Index.slice(prevPos, curPos);
                         var window = tokens.get(Index.Colon, span)) {
                        logits = model.forward(window, prevPos);
                    }

                    Tensor nextToken;
                    try (var last = Index.of(-1);
                         var tail = logits.get(Index.Colon, last)) {
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
                         var textMask = inputTextMask.get(Index.Colon, cur);
                         var currentTokens = tokens.get(Index.Colon, cur);
                         var merged = Tensor.where(textMask, currentTokens, nextToken)) {
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

                    logits.close();
                    nextToken.close();
                    prevPos = curPos;
                    Tensor.pop();
                }

                boolean eos = eosReached.all();
                if (publisher != null && (curPos - chunkPos >= 20 || curPos == totalLen - 1 || eos)) {
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
            Tensor.pop();
            return predictions;
        } finally {
            if (model.kvCachePool() != null) {
                model.kvCachePool().unbindRequests();
            }
        }
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
