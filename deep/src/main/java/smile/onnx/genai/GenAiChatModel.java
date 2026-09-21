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
package smile.onnx.genai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.VideoUrlPart;

/**
 * {@link LanguageModel} adapter over ONNX Runtime GenAI.
 *
 * <p>Holds a shared {@link Model} + {@link Tokenizer} and creates a per-call
 * {@link Generator}. Streaming uses {@link TokenizerStream} and
 * {@link GenerationListener#onText}. Cooperative cancel is checked between
 * {@link Generator#generateNextToken} steps.
 *
 * <p>Serve wires this via ChatService OGA fallback (tools I/O, optional
 * multimodal). Continuous batching via GenAI {@code OgaEngine} is deferred.
 *
 * @author Haifeng Li
 */
public final class GenAiChatModel implements LanguageModel, AutoCloseable {
    /** Architecture family label. */
    static final String FAMILY = "onnx/genai";
    /** Default context length when {@code genai_config.json} is missing the field. */
    private static final int DEFAULT_MAX_SEQ_LEN = 4096;
    /** Matches {@code "context_length": N} in genai_config.json. */
    private static final Pattern CONTEXT_LENGTH = Pattern.compile(
            "\"context_length\"\\s*:\\s*(\\d+)");
    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";
    /**
     * ChatML-style Jinja used when the model tokenizer has no embedded chat template
     * (e.g. onnxruntime-genai {@code test/models/qwen3-5}).
     */
    private static final String DEFAULT_CHAT_TEMPLATE =
            "{% for message in messages %}"
                    + "{% if message['role'] == 'system' %}"
                    + "{{ '<|im_start|>system\\n' + message['content'] + '<|im_end|>\\n' }}"
                    + "{% elif message['role'] == 'user' %}"
                    + "{{ '<|im_start|>user\\n' + message['content'] + '<|im_end|>\\n' }}"
                    + "{% elif message['role'] == 'assistant' %}"
                    + "{{ '<|im_start|>assistant\\n' + message['content'] + '<|im_end|>\\n' }}"
                    + "{% else %}"
                    + "{{ '<|im_start|>' + message['role'] + '\\n' + message['content'] + '<|im_end|>\\n' }}"
                    + "{% endif %}"
                    + "{% endfor %}"
                    + "{% if add_generation_prompt %}{{ '<|im_start|>assistant\\n' }}{% endif %}";

    private final String name;
    private final int maxSeqLen;
    private Model model;
    private Tokenizer tokenizer;
    private MultiModalProcessor processor;

    private GenAiChatModel(String name, int maxSeqLen, Model model, Tokenizer tokenizer) {
        this.name = name;
        this.maxSeqLen = maxSeqLen;
        this.model = model;
        this.tokenizer = tokenizer;
    }

    /**
     * Loads a GenAI chat model from a model directory.
     *
     * @param modelDir directory containing {@code genai_config.json}.
     * @return chat model owned by the caller.
     */
    public static GenAiChatModel of(String modelDir) {
        if (modelDir == null || modelDir.isBlank()) {
            throw new IllegalArgumentException("modelDir must not be blank");
        }
        return of(Path.of(modelDir));
    }

    /**
     * Loads a GenAI chat model from a model directory using providers from
     * {@code genai_config.json}.
     *
     * @param modelDir directory containing {@code genai_config.json}.
     * @return chat model owned by the caller.
     */
    public static GenAiChatModel of(Path modelDir) {
        if (modelDir == null) {
            throw new IllegalArgumentException("modelDir must not be null");
        }
        Model model = Model.of(modelDir);
        try {
            Tokenizer tokenizer = model.createTokenizer();
            Path fileName = modelDir.getFileName();
            String name = fileName != null ? fileName.toString() : modelDir.toString();
            return new GenAiChatModel(name, readMaxSeqLen(modelDir), model, tokenizer);
        } catch (RuntimeException e) {
            model.close();
            throw e;
        }
    }

    /**
     * Opens a GenAI chat model, preferring CUDA when available
     * (see {@link Model#open(Path)}).
     *
     * @param modelDir directory containing {@code genai_config.json}.
     * @return chat model owned by the caller.
     */
    public static GenAiChatModel open(Path modelDir) {
        if (modelDir == null) {
            throw new IllegalArgumentException("modelDir must not be null");
        }
        Model model = Model.open(modelDir);
        try {
            Tokenizer tokenizer = model.createTokenizer();
            Path fileName = modelDir.getFileName();
            String name = fileName != null ? fileName.toString() : modelDir.toString();
            return new GenAiChatModel(name, readMaxSeqLen(modelDir), model, tokenizer);
        } catch (RuntimeException e) {
            model.close();
            throw e;
        }
    }

    /**
     * Opens a GenAI chat model, preferring CUDA when available.
     *
     * @param modelDir directory containing {@code genai_config.json}.
     * @return chat model owned by the caller.
     */
    public static GenAiChatModel open(String modelDir) {
        if (modelDir == null || modelDir.isBlank()) {
            throw new IllegalArgumentException("modelDir must not be blank");
        }
        return open(Path.of(modelDir));
    }

    /**
     * Loads a GenAI chat model from a {@link Config}.
     *
     * @param config GenAI config.
     * @return chat model owned by the caller.
     */
    public static GenAiChatModel of(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        Model model = Model.of(config);
        try {
            Tokenizer tokenizer = model.createTokenizer();
            return new GenAiChatModel(model.name(), DEFAULT_MAX_SEQ_LEN, model, tokenizer);
        } catch (RuntimeException e) {
            model.close();
            throw e;
        }
    }

    @Override
    public String family() {
        return FAMILY;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the GenAI execution-provider path selected at open
     * ({@link Model#provider()}).
     *
     * @return provider id (e.g. {@code cuda}, {@code dml}, {@code default}).
     */
    public String provider() {
        return model.provider();
    }

    @Override
    public int maxSeqLen() {
        return maxSeqLen;
    }

    @Override
    public int[] encodeChat(Message... dialog) {
        return encodeChat(dialog, ChatOptions.NONE);
    }

    @Override
    public int[] encodeChat(Message[] dialog, ChatOptions options) {
        if (dialog == null) {
            throw new IllegalArgumentException("dialog must not be null");
        }
        if (ChatTemplateJson.hasMedia(dialog)) {
            throw new GenAIException(
                    "encodeChat does not accept multimodal parts; use chat() with a "
                            + "vision/audio GenAI model, or pass text-only messages");
        }
        String messagesJson = ChatTemplateJson.messages(dialog);
        String toolsJson = ChatTemplateJson.tools(options);
        String prompt = applyChatTemplate(messagesJson, toolsJson);
        return tokenizer.encodeToArray(prompt);
    }

    /**
     * Applies the model chat template, falling back to a ChatML-style template when
     * the model does not embed one (common for tiny GenAI test fixtures).
     */
    private String applyChatTemplate(String messagesJson, String toolsJson) {
        try {
            return tokenizer.applyChatTemplate(null, messagesJson, toolsJson, true);
        } catch (GenAIException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("empty chat template")) {
                return tokenizer.applyChatTemplate(
                        DEFAULT_CHAT_TEMPLATE, messagesJson, toolsJson, true);
            }
            throw e;
        }
    }

    @Override
    public ChatCompletion generate(int[] prompt, int maxGenLen, double temperature,
                                   double topp, boolean logprobs, long seed,
                                   GenerationListener listener,
                                   BooleanSupplier cancelRequested) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        if (logprobs) {
            throw new UnsupportedOperationException(
                    "GenAiChatModel does not support per-token logprobs yet");
        }
        int promptLen = prompt.length;
        int maxAllowed = Math.max(0, maxSeqLen - promptLen);
        if (maxGenLen > maxAllowed) {
            maxGenLen = maxAllowed;
        }
        if (maxGenLen < 0) {
            maxGenLen = 0;
        }
        int maxLength = Math.min(maxSeqLen, promptLen + maxGenLen);

        if (listener != null) {
            listener.onInputTokens(promptLen);
            listener.onCachedInputTokens(0);
        }

        try (GeneratorParams params = model.createGeneratorParams()) {
            applySearchOptions(params, maxLength, temperature, topp, seed);
            try (Generator generator = Generator.of(model, params)) {
                generator.appendTokens(prompt);
                return runLoop(generator, promptLen, maxGenLen, listener, cancelRequested);
            }
        }
    }

    @Override
    public ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature,
                               double topp, boolean logprobs, long seed,
                               GenerationListener listener,
                               BooleanSupplier cancelRequested) {
        if (dialog == null) {
            throw new IllegalArgumentException("dialog must not be null");
        }
        for (Message message : dialog) {
            for (var part : message.parts()) {
                if (part instanceof VideoUrlPart) {
                    throw new GenAIException(
                            "GenAI does not support video content parts in this adapter");
                }
            }
        }

        if (ChatTemplateJson.hasMedia(dialog)) {
            return chatMultimodal(dialog, maxGenLen, temperature, topp, logprobs, seed,
                    listener, cancelRequested);
        }
        int[] prompt = encodeChat(dialog, ChatOptions.NONE);
        return generate(prompt, maxGenLen, temperature, topp, logprobs, seed,
                listener, cancelRequested);
    }

    private ChatCompletion chatMultimodal(Message[] dialog, int maxGenLen, double temperature,
                                          double topp, boolean logprobs, long seed,
                                          GenerationListener listener,
                                          BooleanSupplier cancelRequested) {
        if (logprobs) {
            throw new UnsupportedOperationException(
                    "GenAiChatModel does not support per-token logprobs yet");
        }
        String[] imagePaths = ChatTemplateJson.imagePaths(dialog);
        String[] audioPaths = ChatTemplateJson.audioPaths(dialog);
        String messagesJson = ChatTemplateJson.messages(dialog);
        String promptText = applyChatTemplate(messagesJson, null);
        // Text-only length is a lower bound used to size max_length before setInputs.
        int textPromptLen = tokenizer.encodeToArray(promptText).length;
        int maxLength = Math.min(maxSeqLen, textPromptLen + Math.max(0, maxGenLen));

        MultiModalProcessor proc = processor();
        try (Images images = imagePaths.length == 0 ? null : Images.load(imagePaths);
             Audios audios = audioPaths.length == 0 ? null : Audios.load(audioPaths);
             NamedTensors inputs = (images != null && audios != null)
                     ? proc.processImagesAndAudios(promptText, images, audios)
                     : images != null
                     ? proc.processImages(promptText, images)
                     : proc.processAudios(promptText, audios);
             GeneratorParams params = model.createGeneratorParams()) {

            applySearchOptions(params, Math.max(1, maxLength), temperature, topp, seed);
            try (Generator generator = Generator.of(model, params)) {
                generator.setInputs(inputs);
                int promptLen = (int) generator.tokenCount();
                if (listener != null) {
                    listener.onInputTokens(promptLen);
                    listener.onCachedInputTokens(0);
                }
                int genBudget = Math.min(maxGenLen, Math.max(0, maxSeqLen - promptLen));
                return runLoop(generator, promptLen, genBudget, listener, cancelRequested);
            }
        }
    }

    private ChatCompletion runLoop(Generator generator, int promptLen, int maxGenLen,
                                   GenerationListener listener,
                                   BooleanSupplier cancelRequested) {
        StringBuilder text = new StringBuilder();
        int generated = 0;
        boolean stopped = false;
        ThinkStream think = new ThinkStream();

        try (TokenizerStream stream = tokenizer.createStream()) {
            while (!generator.isDone()) {
                throwIfCancelled(cancelRequested);
                if (maxGenLen > 0 && generated >= maxGenLen) {
                    break;
                }
                generator.generateNextToken();
                generated++;
                String chunk = stream.decode(generator.getLastToken(0));
                if (chunk != null && !chunk.isEmpty()) {
                    text.append(chunk);
                    boolean thinking = think.feed(chunk, listener);
                    if (listener != null) {
                        if (thinking) {
                            listener.onThinkingTokens(1);
                        } else {
                            listener.onGeneratedTokens(1);
                        }
                    }
                } else if (listener != null) {
                    listener.onGeneratedTokens(1);
                }
                if (generator.isDone()) {
                    stopped = true;
                }
            }
            if (generator.isDone()) {
                stopped = true;
            }
            think.flush(listener);
        }

        int[] full = generator.getSequence(0);
        int[] completion = full.length <= promptLen
                ? new int[0]
                : Arrays.copyOfRange(full, promptLen, full.length);
        String raw = !text.isEmpty()
                ? text.toString()
                : (completion.length == 0 ? "" : tokenizer.decode(completion));
        String content = smile.llm.tool.AssistantTextSanitizer.sanitize(raw);
        if (content == null) {
            content = "";
        }
        int[] promptTokens = full.length >= promptLen
                ? Arrays.copyOf(full, promptLen)
                : full;
        FinishReason reason = stopped ? FinishReason.stop : FinishReason.length;
        return new ChatCompletion(name, content, promptTokens, completion, reason, null);
    }

    /**
     * Streaming strip of {@code <think>…</think>} with visible {@code onText} emits.
     * Token attribution uses {@link #inThink} at feed time (caller reports counts).
     */
    static final class ThinkStream {
        private final StringBuilder hold = new StringBuilder();
        private boolean inThink;

        /**
         * Feeds a decoded chunk; emits visible text via {@code listener.onText}.
         *
         * @return {@code true} when this chunk is attributed to thinking.
         */
        boolean feed(String chunk, GenerationListener listener) {
            boolean wasInThink = inThink;
            hold.append(chunk);
            drain(listener);
            return wasInThink || inThink || hold.indexOf(THINK_OPEN) >= 0;
        }

        void flush(GenerationListener listener) {
            if (!inThink && !hold.isEmpty()) {
                String rest = hold.toString();
                // Drop a trailing incomplete open tag.
                int partial = incompleteOpenTag(rest);
                if (partial >= 0) {
                    rest = rest.substring(0, partial);
                }
                hold.setLength(0);
                if (!rest.isEmpty() && listener != null) {
                    listener.onText(rest);
                }
            }
        }

        private void drain(GenerationListener listener) {
            String s = hold.toString();
            while (true) {
                if (inThink) {
                    int close = s.indexOf(THINK_CLOSE);
                    if (close < 0) {
                        hold.setLength(0);
                        hold.append(s);
                        return;
                    }
                    s = s.substring(close + THINK_CLOSE.length());
                    inThink = false;
                    continue;
                }
                int open = s.indexOf(THINK_OPEN);
                if (open >= 0) {
                    if (open > 0) {
                        emit(s.substring(0, open), listener);
                    }
                    s = s.substring(open + THINK_OPEN.length());
                    inThink = true;
                    continue;
                }
                // Keep a suffix that might be a partial "<think>" open tag.
                int keep = Math.min(s.length(), THINK_OPEN.length() - 1);
                if (s.length() > keep) {
                    emit(s.substring(0, s.length() - keep), listener);
                    s = s.substring(s.length() - keep);
                }
                hold.setLength(0);
                hold.append(s);
                return;
            }
        }

        private static void emit(String text, GenerationListener listener) {
            if (listener != null && text != null && !text.isEmpty()) {
                listener.onText(text);
            }
        }

        private static int incompleteOpenTag(String s) {
            for (int n = Math.min(s.length(), THINK_OPEN.length() - 1); n >= 1; n--) {
                if (THINK_OPEN.startsWith(s.substring(s.length() - n))) {
                    return s.length() - n;
                }
            }
            return -1;
        }
    }

    private static void applySearchOptions(GeneratorParams params, int maxLength,
                                           double temperature, double topp, long seed) {
        params.setSearchOption("max_length", (double) maxLength);
        if (temperature > 0) {
            params.setSearchOption("temperature", temperature);
            params.setSearchOption("do_sample", true);
        } else {
            params.setSearchOption("do_sample", false);
        }
        if (topp > 0 && topp < 1.0) {
            params.setSearchOption("top_p", topp);
        }
        if (seed != 0) {
            params.setSearchOption("random_seed", (double) seed);
        }
    }

    private MultiModalProcessor processor() {
        if (processor == null) {
            try {
                processor = MultiModalProcessor.of(model);
            } catch (GenAIException e) {
                throw new GenAIException(
                        "This GenAI model does not support multimodal inputs", e);
            }
        }
        return processor;
    }

    private static void throwIfCancelled(BooleanSupplier cancelRequested) {
        if (cancelRequested != null && cancelRequested.getAsBoolean()) {
            throw new CancellationException("aborted");
        }
    }

    private static int readMaxSeqLen(Path modelDir) {
        Path config = modelDir.resolve("genai_config.json");
        if (!Files.isRegularFile(config)) {
            return DEFAULT_MAX_SEQ_LEN;
        }
        try {
            String json = Files.readString(config);
            Matcher m = CONTEXT_LENGTH.matcher(json);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (IOException | NumberFormatException ignored) {
            // fall through to default
        }
        return DEFAULT_MAX_SEQ_LEN;
    }

    @Override
    public void close() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        if (tokenizer != null) {
            tokenizer.close();
            tokenizer = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
    }
}
