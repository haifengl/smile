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
import smile.llm.Message;
import smile.llm.cache.KvCachePool;
import smile.llm.transformer.ModelArgs;
import smile.llm.transformer.Transformer;
import smile.torch.smile_torch_h;
import smile.util.AutoScope;

/**
 * LLaMA model specification.
 *
 * @author Haifeng Li
 */
public class Llama {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Llama.class);
    /** The model family name. */
    static final String family = "meta/llama3";
    /** Matches HuggingFace layer weight names such as {@code model.layers.12.self_attn.q_proj.weight}. */
    private static final Pattern HF_LAYER_WEIGHT = Pattern.compile(
            "^model\\.layers\\.(\\d+)\\.(self_attn|mlp|input_layernorm|post_attention_layernorm)\\.(.+)$");
    /** The model instance name. */
    final String name;
    /** The transformer model. */
    final Transformer model;
    /** The tokenizer. */
    final Tokenizer tokenizer;

    /**
     * Constructor.
     * @param name the model name.
     * @param model the transformer model.
     * @param tokenizer the tokenizer.
     */
    public Llama(String name, Transformer model, Tokenizer tokenizer) {
        this.name = name;
        this.model = model;
        this.tokenizer = tokenizer;
    }

    @Override
    public String toString() {
        return String.format("%s/%s", family, name);
    }

    /**
     * Returns the model family name.
     * @return the model family name.
     */
    public String family() {
        return family;
    }

    /**
     * Returns the model instance name.
     * @return the model instance name.
     */
    public String name() {
        return name;
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
        return build(checkpointDir, tokenizerPath, maxBatchSize, maxSeqLen, deviceId, 0);
    }

    /**
     * Builds a Llama instance by initializing and loading a model checkpoint.
     *
     * <p>When {@code memFractionStatic > 0}, a {@link KvCachePool} is allocated
     * after weight loading using that fraction of the remaining free device
     * memory (see {@code smile.mem.fraction.static} in smile-serve).
     *
     * @param checkpointDir the directory path of checkpoint files.
     * @param tokenizerPath the path of tokenizer model file.
     * @param maxBatchSize the maximum batch size for inference.
     * @param maxSeqLen the maximum sequence length for input text.
     * @param deviceId the optional CUDA device ID. If negative, don't use CUDA.
     * @param memFractionStatic fraction of free GPU memory for the KV cache pool;
     *                          {@code <= 0} keeps the default test-sized pool.
     * @throws IOException if fail to open model checkpoint.
     * @return an instance of Llama model.
     */
    public static Llama build(String checkpointDir, String tokenizerPath, int maxBatchSize,
                              int maxSeqLen, byte deviceId, double memFractionStatic) throws IOException {
        File dir = new File(checkpointDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Checkpoint directory doesn't exist: " + checkpointDir);
        }

        String worldSize = Objects.requireNonNullElse(System.getenv("WORLD_SIZE"), "1");
        int modelParallelSize = Integer.parseInt(worldSize);
        String localRank = Objects.requireNonNullElse(System.getenv("LOCAL_RANK"), "0");
        int rank = Integer.parseInt(localRank);

        Device device = Device.CPU();
        ScalarType cacheDtype = ScalarType.Float;
        if (deviceId >= 0) {
            var startTime = System.currentTimeMillis();
            device = Device.CUDA(deviceId);

            // half precision to lower memory usage.
            cacheDtype = Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half;
            smile_torch_h.smile_set_default_dtype(cacheDtype.code());
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

        ModelArgs modelArgs;
        if (huggingFace) {
            modelArgs = ModelArgs.fromHuggingFace(configJson.toString(), maxBatchSize, maxSeqLen);
        } else if (Files.exists(paramsJson)) {
            modelArgs = ModelArgs.from(paramsJson.toString(), maxBatchSize, maxSeqLen);
        } else if (Files.exists(configJson)) {
            huggingFace = true;
            modelArgs = ModelArgs.fromHuggingFace(configJson.toString(), maxBatchSize, maxSeqLen);
        } else {
            throw new IllegalArgumentException(
                    "Neither params.json nor config.json found in " + checkpointDir);
        }

        var tokenizer = Tokenizer.of(tokenizerPath);
        if (tokenizer.size() != modelArgs.vocabSize()) {
            throw new IllegalStateException("Tokenizer and ModelArgs have different vocabulary size.");
        }

        // When a static memory fraction is configured, use a tiny CPU placeholder
        // pool during weight load, then replace it with a GPU pool sized from
        // residual free memory. Avoids allocating a full maxBatch×maxSeqLen CUDA
        // bootstrap cache (and related empty-tensor pitfalls) before weights load.
        KvCachePool bootstrap = memFractionStatic > 0
                ? KvCachePool.bootstrap(modelArgs)
                : KvCachePool.forTesting(modelArgs, device);
        var model = new Transformer(modelArgs, device, bootstrap);

        if (huggingFace) {
            loadHuggingFaceWeights(model, dir, device);
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
        model.eval();

        // Size the shared KV cache from residual free memory after weights load.
        if (memFractionStatic > 0) {
            model.kvCachePool().close();
            device.emptyCache();
            var pool = KvCachePool.allocate(modelArgs, device, cacheDtype, memFractionStatic);
            model.setKvCachePool(pool, false);
        }

        var time = System.currentTimeMillis() - startTime;
        logger.info("Model {}[{}]: loaded in {}.{} seconds", checkpointDir, rank, time/1000, time%1000);
        return new Llama(dir.getName(), model, tokenizer);
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
     * <p>Weight names are remapped from the HuggingFace convention
     * ({@code model.layers.N.self_attn.q_proj.weight}, …) to the Meta / SMILE
     * convention ({@code layers.N.attention.wq.weight}, …). Query and key
     * projection weights are reverse-permuted so they match SMILE's Meta-style
     * RoPE layout.
     *
     * @param model the transformer to load into.
     * @param dir the HuggingFace model directory.
     * @param device the device on which tensors are materialised.
     * @throws IOException if a weight file cannot be read.
     */
    private static void loadHuggingFaceWeights(Transformer model, File dir, Device device) throws IOException {
        Map<String, String> weightMap = readWeightMap(dir);
        // Group tensor names by shard file for memory-efficient loading.
        Map<String, List<String>> shardToKeys = new LinkedHashMap<>();
        for (var entry : weightMap.entrySet()) {
            shardToKeys.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        int numHeads = model.params().numHeads();
        int numKvHeads = model.params().numKvHeads() != null
                ? model.params().numKvHeads() : numHeads;
        Set<String> loaded = new HashSet<>();

        for (var shardEntry : shardToKeys.entrySet()) {
            String shardFile = shardEntry.getKey();
            Path shardPath = Path.of(dir.getPath(), shardFile);
            logger.info("Loading safetensors shard: {}", shardFile);
            SafeTensors st = SafeTensors.read(shardPath.toString(), device);
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
                        if (smileName.endsWith(".attention.wq.weight")) {
                            value = reversePermute(src, numHeads);
                            owned.add(value);
                        } else if (smileName.endsWith(".attention.wk.weight")) {
                            value = reversePermute(src, numKvHeads);
                            owned.add(value);
                        }
                        stateDict.put(smileName, value);
                        loaded.add(smileName);
                    }

                    // Tied embeddings: some checkpoints omit lm_head.weight.
                    if (st.tensors().containsKey("model.embed_tokens.weight")
                            && !weightMap.containsKey("lm_head.weight")
                            && !loaded.contains("output.weight")) {
                        stateDict.put("output.weight", st.tensors().get("model.embed_tokens.weight"));
                        loaded.add("output.weight");
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

        logger.info("Loaded {} parameters from HuggingFace safetensors", loaded.size());
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

        // Single-file checkpoint: discover tensor names from the file header.
        SafeTensors st = SafeTensors.read(Path.of(dir.getPath(), shards.getFirst()).toString(), Device.CPU());
        try {
            Map<String, String> map = new LinkedHashMap<>();
            for (String name : st.tensors().keySet()) {
                map.put(name, shards.getFirst());
            }
            return map;
        } finally {
            for (Tensor t : st.tensors().values()) {
                t.close();
            }
        }
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
            return new Tensor(smile_torch_h.smile_tensor_clone(reshaped.handle()));
        }
    }

    /**
     * Generates text sequences based on provided prompts. This method uses
     * the provided prompts as a basis for generating text. It employs nucleus
     * sampling to produce text with controlled randomness.
     * @param prompts List of tokenized prompts, where each prompt is represented as a list of integers.
     * @param maxGenLen Maximum length of the generated text sequence.
     * @param temperature Temperature value for controlling randomness in sampling.
     * @param topp Top-p probability threshold for nucleus sampling.
     * @param logprobs Flag indicating whether to compute token log probabilities.
     * @param seed the optional random number generation seed to sample deterministically.
     * @param publisher an optional flow publisher that asynchronously issues generated chunks.
     * The batch size must be 1.
     * @return The generated text completion.
     */
    public ChatCompletion[] generate(int[][] prompts, int maxGenLen, double temperature,
                                     double topp, boolean logprobs, long seed,
                                     SubmissionPublisher<String> publisher) {
        int batchSize = prompts.length;
        if (batchSize > model.params().maxBatchSize()) {
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
        if (maxPromptLen > model.params().maxSeqLen()) {
            throw new IllegalArgumentException("The prompt length is greater than max_seq_len");
        }

        // seed must be the same in all processes
        if (seed != 0) {
            smile_torch_h.smile_manual_seed(seed);
        }

        try (var guard = Tensor.noGradGuard();
             var scope = new AutoScope()) {
            Tensor.push(scope);
            int totalLen = Math.min(model.params().maxSeqLen(), maxGenLen + maxPromptLen);
            model.kvCachePool().bindRequests(batchSize, totalLen);

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
            if (minPromptLen == totalLen) {
                try (var logits = model.forward(tokens, prevPos)) {
                    if (logprobs) {
                        try (var transposed = logits.transpose(1, 2)) {
                            tokenLogprobs = Tensor.crossEntropy(transposed, tokens, "none", pad).neg_();
                        }
                    }
                }
            }

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
                    // only replace token if prompt has already been generated
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
                    // Free up memory at each iteration
                    Tensor.pop();
                }

                boolean eos = eosReached.all();
                if (publisher != null && (curPos - chunkPos >= 20 || curPos == totalLen-1 || eos)) {
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
                            var chunk = tokenizer.tryDecode(completion);
                            publisher.submit(chunk);
                            chunkPos = curPos + 1;
                        } catch (Exception ex) {
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
                // cut to max gen len
                int start = prompts[i].length;
                var completion = Arrays.stream(longArray)
                        .skip((long) i * totalLen + start)
                        .mapToInt(x -> (int) x)
                        .limit(prompts[i].length + maxGenLen - start)
                        .toArray();

                float[] probs = null;
                if (logprobs) {
                    probs = Arrays.copyOfRange(logprobArray, i * totalLen + start, i * totalLen + prompts[i].length + maxGenLen);
                }

                // cut to after eos tok if any
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
                predictions[i] = new ChatCompletion(name, tokenizer.decode(completion), prompts[i], completion, reason, probs);
            }

            if (publisher != null) publisher.close();
            Tensor.pop();
            return predictions;
        } finally {
            model.kvCachePool().unbindRequests();
        }
    }

    /**
     * Performs text completion for a list of prompts
     * @param prompts List of text prompts.
     * @param maxGenLen Maximum length of the generated text sequence.
     * @param temperature Temperature value for controlling randomness in sampling.
     * @param topp Top-p probability threshold for nucleus sampling.
     * @param logprobs Flag indicating whether to compute token log probabilities.
     * @param seed the optional random number generation seed to sample deterministically.
     * @param publisher an optional flow publisher that asynchronously issues generated chunks.
     * The batch size must be 1.
     * @return The generated text completion.
     */
    public ChatCompletion[] complete(String[] prompts, int maxGenLen, double temperature, double topp, boolean logprobs, long seed, SubmissionPublisher<String> publisher) {
        int batchSize = prompts.length;
        int[][] tokens = new int[batchSize][];
        for (int i = 0; i < batchSize; i++) {
            tokens[i] = tokenizer.encode(prompts[i], true, false);
        }

        return generate(tokens, maxGenLen, temperature, topp, logprobs, seed, publisher);
    }

    /**
     * Generates assistant responses for a list of conversational dialogs.
     * @param dialogs List of conversational dialogs, where each dialog is a list of messages.
     * @param maxGenLen Maximum length of the generated text sequence.
     * @param temperature Temperature value for controlling randomness in sampling.
     * @param topp Top-p probability threshold for nucleus sampling.
     * @param logprobs Flag indicating whether to compute token log probabilities.
     * @param seed the optional random number generation seed to sample deterministically.
     * @param publisher an optional flow publisher that asynchronously issues generated chunks.
     * The batch size must be 1.
     * @return The generated chat responses.
     */
    public ChatCompletion[] chat(Message[][] dialogs, int maxGenLen, double temperature, double topp, boolean logprobs, long seed, SubmissionPublisher<String> publisher) {
        int batchSize = dialogs.length;
        int[][] tokens = new int[batchSize][];
        for (int i = 0; i < batchSize; i++) {
            tokens[i] = tokenizer.encodeDialog(dialogs[i]);
        }

        return generate(tokens, maxGenLen, temperature, topp, logprobs, seed, publisher);
    }
}
