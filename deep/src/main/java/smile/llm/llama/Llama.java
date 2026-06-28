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
import java.util.*;
import java.util.concurrent.SubmissionPublisher;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.Message;
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
     * @param checkpointDir the directory path of checkpoint files.
     * @param tokenizerPath the path of tokenizer model file.
     * @param maxSeqLen the maximum sequence length for input text.
     * @param maxBatchSize the maximum batch size for inference.
     * @param deviceId the optional CUDA device ID. If negative, don't use CUDA.
     * @throws IOException if fail to open model checkpoint.
     * @return an instance of Llama model.
     */
    public static Llama build(String checkpointDir, String tokenizerPath, int maxBatchSize, int maxSeqLen, byte deviceId) throws IOException {
        File dir = new File(checkpointDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Checkpoint directory doesn't exist: " + checkpointDir);
        }

        String worldSize = Objects.requireNonNullElse(System.getenv("WORLD_SIZE"), "1");
        int modelParallelSize = Integer.parseInt(worldSize);
        String localRank = Objects.requireNonNullElse(System.getenv("LOCAL_RANK"), "0");
        int rank = Integer.parseInt(localRank);

        Device device = Device.CPU();
        if (deviceId >= 0) {
            var startTime = System.currentTimeMillis();
            device = Device.CUDA(deviceId);

            // half precision to lower memory usage.
            smile_torch_h.smile_set_default_dtype(
                    (Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half).code());
            var time = System.currentTimeMillis() - startTime;
            logger.info("Initialized CUDA[{}]: {}.{} seconds", deviceId, time / 1000, time % 1000);
        }

        var options = new Tensor.Options().device(device).requireGradients(false);
        Tensor.setDefaultOptions(options);

        var startTime = System.currentTimeMillis();
        List<String> checkpoints = getCheckpoints(dir);
        if (checkpoints.isEmpty()) {
            throw new IllegalArgumentException("No checkpoint files found in " + checkpointDir);
        }

        if (checkpoints.size() != modelParallelSize) {
            throw new IllegalStateException(String.format("Loading a checkpoint for MP=%d but world size is %d", checkpoints.size(), modelParallelSize));
        }

        var modelArgs = ModelArgs.from(checkpointDir + "/params.json", maxBatchSize, maxSeqLen);

        var tokenizer = Tokenizer.of(tokenizerPath);
        if (tokenizer.size() != modelArgs.vocabSize()) {
            throw new IllegalStateException("Tokenizer and ModelArgs have different vocabulary size.");
        }

        var model = new Transformer(modelArgs, device);
        model.eval();
        Collections.sort(checkpoints);
        var checkpoint = checkpoints.get(rank);
        model.load(checkpoint);

        var time = System.currentTimeMillis() - startTime;
        logger.info("Model {}[{}]: loaded in {}.{} seconds", checkpointDir, rank, time/1000, time%1000);
        return new Llama(dir.getName(), model, tokenizer);
    }

    /**
     * Returns the checkpoint file paths.
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
                var options = new Tensor.Options().device(model.device()).requireGradients(false).dtype(ScalarType.Float);
                tokenLogprobs = Tensor.zeros(options, batchSize, totalLen);
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
