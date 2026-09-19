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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Map;
import smile.onnx.genai.foreign.ort_genai_c_h;

/**
 * Converts between text and GenAI token ids, including chat templates.
 *
 * @author Haifeng Li
 */
public final class Tokenizer implements AutoCloseable {
    /** Native {@code OgaTokenizer*} handle. */
    private MemorySegment handle;

    Tokenizer(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Creates a tokenizer from a loaded model.
     *
     * @param model the model.
     * @return a new tokenizer owned by the caller.
     */
    public static Tokenizer of(Model model) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            return new Tokenizer(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateTokenizer(model.handle(), out)));
        }
    }

    /**
     * Creates a tokenizer from a config.
     *
     * @param config the config.
     * @return a new tokenizer owned by the caller.
     */
    public static Tokenizer of(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            return new Tokenizer(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateTokenizerFromConfig(config.handle(), out)));
        }
    }

    /**
     * Creates a tokenizer from a model directory path.
     *
     * @param modelDir GenAI model directory.
     * @return a new tokenizer owned by the caller.
     */
    public static Tokenizer of(String modelDir) {
        if (modelDir == null || modelDir.isBlank()) {
            throw new IllegalArgumentException("modelDir must not be blank");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            return new Tokenizer(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateTokenizerFromPath(
                            arena.allocateFrom(modelDir), out)));
        }
    }

    /**
     * Creates a tokenizer from a model directory path.
     *
     * @param modelDir GenAI model directory.
     * @return a new tokenizer owned by the caller.
     */
    public static Tokenizer of(Path modelDir) {
        if (modelDir == null) {
            throw new IllegalArgumentException("modelDir must not be null");
        }
        return of(modelDir.toString());
    }

    /**
     * Encodes a single string into a {@link Sequences} with one sequence.
     *
     * @param text text to encode.
     * @return sequences owned by the caller.
     */
    public Sequences encode(String text) {
        return encodeBatch(new String[]{text});
    }

    /**
     * Encodes multiple strings into one {@link Sequences} (one entry per string).
     *
     * @param texts texts to encode.
     * @return sequences owned by the caller.
     */
    public Sequences encodeBatch(String[] texts) {
        GenAIRuntime.requireOpen(handle, "Tokenizer");
        if (texts == null || texts.length == 0) {
            throw new IllegalArgumentException("texts must not be empty");
        }
        Sequences sequences = Sequences.create();
        try (Arena arena = Arena.ofConfined()) {
            for (String text : texts) {
                if (text == null) {
                    sequences.close();
                    throw new IllegalArgumentException("texts must not contain null");
                }
                GenAIRuntime.checkResult(ort_genai_c_h.OgaTokenizerEncode(
                        handle, arena.allocateFrom(text), sequences.handle()));
            }
        } catch (RuntimeException e) {
            sequences.close();
            throw e;
        }
        return sequences;
    }

    /**
     * Encodes text and returns the first sequence as an {@code int[]}.
     *
     * @param text text to encode.
     * @return token ids.
     */
    public int[] encodeToArray(String text) {
        try (Sequences sequences = encode(text)) {
            return sequences.get(0);
        }
    }

    /**
     * Decodes token ids to text.
     *
     * @param tokens token ids.
     * @return decoded text.
     */
    public String decode(int[] tokens) {
        GenAIRuntime.requireOpen(handle, "Tokenizer");
        if (tokens == null) {
            throw new IllegalArgumentException("tokens must not be null");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocateFrom(ValueLayout.JAVA_INT, tokens);
            MemorySegment out = arena.allocate(ort_genai_c_h.C_POINTER);
            GenAIRuntime.checkResult(
                    ort_genai_c_h.OgaTokenizerDecode(handle, buf, tokens.length, out));
            return GenAIRuntime.takeString(out.get(ort_genai_c_h.C_POINTER, 0));
        }
    }

    /**
     * Decodes each sequence in a {@link Sequences} to text.
     *
     * @param sequences encoded sequences.
     * @return one string per sequence.
     */
    public String[] decodeBatch(Sequences sequences) {
        if (sequences == null) {
            throw new IllegalArgumentException("sequences must not be null");
        }
        int n = (int) sequences.count();
        String[] result = new String[n];
        for (int i = 0; i < n; i++) {
            result[i] = decode(sequences.get(i));
        }
        return result;
    }

    /**
     * Applies the model's chat template.
     *
     * @param templateStr         template string, or {@code null} to use the model default.
     * @param messagesJson        messages as a JSON array string.
     * @param toolsJson           tools JSON, or {@code null} when unused.
     * @param addGenerationPrompt whether to append the assistant generation prompt.
     * @return formatted prompt text.
     */
    public String applyChatTemplate(String templateStr, String messagesJson,
                                    String toolsJson, boolean addGenerationPrompt) {
        GenAIRuntime.requireOpen(handle, "Tokenizer");
        if (messagesJson == null) {
            throw new IllegalArgumentException("messagesJson must not be null");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment template = templateStr == null
                    ? MemorySegment.NULL
                    : arena.allocateFrom(templateStr);
            MemorySegment tools = toolsJson == null
                    ? MemorySegment.NULL
                    : arena.allocateFrom(toolsJson);
            MemorySegment out = arena.allocate(ort_genai_c_h.C_POINTER);
            GenAIRuntime.checkResult(ort_genai_c_h.OgaTokenizerApplyChatTemplate(
                    handle,
                    template,
                    arena.allocateFrom(messagesJson),
                    tools,
                    addGenerationPrompt,
                    out));
            return GenAIRuntime.takeString(out.get(ort_genai_c_h.C_POINTER, 0));
        }
    }

    /**
     * Updates tokenizer options (key/value string pairs).
     *
     * @param options option map; no-op when null or empty.
     */
    public void updateOptions(Map<String, String> options) {
        GenAIRuntime.requireOpen(handle, "Tokenizer");
        if (options == null || options.isEmpty()) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            int n = options.size();
            MemorySegment keys = arena.allocate(ort_genai_c_h.C_POINTER, n);
            MemorySegment values = arena.allocate(ort_genai_c_h.C_POINTER, n);
            int i = 0;
            for (Map.Entry<String, String> e : options.entrySet()) {
                keys.setAtIndex(ort_genai_c_h.C_POINTER, i, arena.allocateFrom(e.getKey()));
                values.setAtIndex(ort_genai_c_h.C_POINTER, i, arena.allocateFrom(e.getValue()));
                i++;
            }
            GenAIRuntime.checkResult(
                    ort_genai_c_h.OgaUpdateTokenizerOptions(handle, keys, values, n));
        }
    }

    /**
     * Creates a streaming decoder for use with {@link Generator}.
     *
     * @return a new stream owned by the caller.
     */
    public TokenizerStream createStream() {
        GenAIRuntime.requireOpen(handle, "Tokenizer");
        try (Arena arena = Arena.ofConfined()) {
            return new TokenizerStream(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateTokenizerStream(handle, out)));
        }
    }

    /**
     * Returns the BOS token id.
     *
     * @return BOS token id.
     */
    public int bosTokenId() {
        return getTokenId(ort_genai_c_h::OgaTokenizerGetBosTokenId);
    }

    /**
     * Returns the PAD token id.
     *
     * @return PAD token id.
     */
    public int padTokenId() {
        return getTokenId(ort_genai_c_h::OgaTokenizerGetPadTokenId);
    }

    /**
     * Converts a string to a single token id.
     *
     * @param token token string.
     * @return token id.
     */
    public int toTokenId(String token) {
        GenAIRuntime.requireOpen(handle, "Tokenizer");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ValueLayout.JAVA_INT);
            GenAIRuntime.checkResult(ort_genai_c_h.OgaTokenizerToTokenId(
                    handle, arena.allocateFrom(token), out));
            return out.get(ValueLayout.JAVA_INT, 0);
        }
    }

    private int getTokenId(java.util.function.BiFunction<MemorySegment, MemorySegment, MemorySegment> fn) {
        GenAIRuntime.requireOpen(handle, "Tokenizer");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ValueLayout.JAVA_INT);
            GenAIRuntime.checkResult(fn.apply(handle, out));
            return out.get(ValueLayout.JAVA_INT, 0);
        }
    }

    /** @return the native handle (package use). */
    MemorySegment handle() {
        GenAIRuntime.requireOpen(handle, "Tokenizer");
        return handle;
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyTokenizer(handle);
            handle = MemorySegment.NULL;
        }
    }
}
