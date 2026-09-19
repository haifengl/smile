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
import java.util.Iterator;
import java.util.NoSuchElementException;
import smile.onnx.genai.foreign.ort_genai_c_h;

/**
 * Autoregressive token generator. Canonical loop:
 * <pre>{@code
 * generator.appendTokens(promptTokens);
 * try (var stream = tokenizer.createStream()) {
 *     while (!generator.isDone()) {
 *         generator.generateNextToken();
 *         String chunk = stream.decode(generator.getLastToken(0));
 *     }
 * }
 * }</pre>
 *
 * <p>Also implements {@link Iterable}{@code Integer>} so callers can write
 * {@code for (int token : generator)}.
 *
 * @author Haifeng Li
 */
public final class Generator implements AutoCloseable, Iterable<Integer> {
    /** Native {@code OgaGenerator*} handle. */
    private MemorySegment handle;

    Generator(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Creates a generator from a model and search parameters.
     *
     * @param model  the model (must outlive this generator).
     * @param params search parameters.
     * @return a new generator owned by the caller.
     */
    public static Generator of(Model model, GeneratorParams params) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("params must not be null");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            return new Generator(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateGenerator(
                            model.handle(), params.handle(), out)));
        }
    }

    /**
     * Returns whether generation has finished.
     *
     * @return {@code true} when no more tokens will be produced.
     */
    public boolean isDone() {
        GenAIRuntime.requireOpen(handle, "Generator");
        return ort_genai_c_h.OgaGenerator_IsDone(handle);
    }

    /**
     * Appends prompt token ids.
     *
     * @param tokens prompt tokens.
     */
    public void appendTokens(int[] tokens) {
        GenAIRuntime.requireOpen(handle, "Generator");
        if (tokens == null) {
            throw new IllegalArgumentException("tokens must not be null");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocateFrom(ValueLayout.JAVA_INT, tokens);
            GenAIRuntime.checkResult(
                    ort_genai_c_h.OgaGenerator_AppendTokens(handle, buf, tokens.length));
        }
    }

    /**
     * Appends encoded sequences as the prompt.
     *
     * @param sequences token sequences (typically from {@link Tokenizer#encode}).
     */
    public void appendSequences(Sequences sequences) {
        GenAIRuntime.requireOpen(handle, "Generator");
        if (sequences == null) {
            throw new IllegalArgumentException("sequences must not be null");
        }
        GenAIRuntime.checkResult(
                ort_genai_c_h.OgaGenerator_AppendTokenSequences(handle, sequences.handle()));
    }

    /**
     * Sets a single named model input tensor.
     *
     * @param name   input name.
     * @param tensor input tensor.
     */
    public void setModelInput(String name, Tensor tensor) {
        GenAIRuntime.requireOpen(handle, "Generator");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaGenerator_SetModelInput(
                    handle, arena.allocateFrom(name), tensor.handle()));
        }
    }

    /**
     * Sets multimodal / multi-input tensors (e.g. from {@link MultiModalProcessor}).
     *
     * @param namedTensors named inputs.
     */
    public void setInputs(NamedTensors namedTensors) {
        GenAIRuntime.requireOpen(handle, "Generator");
        if (namedTensors == null) {
            throw new IllegalArgumentException("namedTensors must not be null");
        }
        GenAIRuntime.checkResult(
                ort_genai_c_h.OgaGenerator_SetInputs(handle, namedTensors.handle()));
    }

    /**
     * Generates the next token (computes logits and samples).
     */
    public void generateNextToken() {
        GenAIRuntime.requireOpen(handle, "Generator");
        GenAIRuntime.checkResult(ort_genai_c_h.OgaGenerator_GenerateNextToken(handle));
    }

    /**
     * Returns the current token count in the generator state.
     *
     * @return token count.
     */
    public long tokenCount() {
        GenAIRuntime.requireOpen(handle, "Generator");
        return ort_genai_c_h.OgaGenerator_TokenCount(handle);
    }

    /**
     * Rewinds generation state to the given length.
     *
     * @param newLength desired length in tokens after rewind.
     */
    public void rewindTo(long newLength) {
        GenAIRuntime.requireOpen(handle, "Generator");
        GenAIRuntime.checkResult(ort_genai_c_h.OgaGenerator_RewindTo(handle, newLength));
    }

    /**
     * Returns a copy of the full token sequence at {@code index}.
     *
     * @param index sequence index (usually {@code 0}).
     * @return token ids including the prompt.
     */
    public int[] getSequence(long index) {
        GenAIRuntime.requireOpen(handle, "Generator");
        long n = ort_genai_c_h.OgaGenerator_GetSequenceCount(handle, index);
        if (n <= 0) {
            return new int[0];
        }
        MemorySegment data = ort_genai_c_h.OgaGenerator_GetSequenceData(handle, index);
        if (data == null || data.equals(MemorySegment.NULL)) {
            throw new GenAIException("OgaGenerator_GetSequenceData returned NULL");
        }
        return data.reinterpret(n * ValueLayout.JAVA_INT.byteSize())
                .toArray(ValueLayout.JAVA_INT);
    }

    /**
     * Returns the last token in the sequence at {@code index}.
     *
     * @param index sequence index (usually {@code 0}).
     * @return last token id.
     */
    public int getLastToken(long index) {
        int[] seq = getSequence(index);
        if (seq.length == 0) {
            throw new GenAIException("sequence is empty");
        }
        return seq[seq.length - 1];
    }

    /**
     * Activates a previously loaded LoRA adapter for this generator.
     *
     * @param adapters    adapter container.
     * @param adapterName adapter name passed to {@link Adapters#load}.
     */
    public void setActiveAdapter(Adapters adapters, String adapterName) {
        GenAIRuntime.requireOpen(handle, "Generator");
        if (adapters == null) {
            throw new IllegalArgumentException("adapters must not be null");
        }
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaSetActiveAdapter(
                    handle, adapters.handle(), arena.allocateFrom(adapterName)));
        }
    }

    /**
     * Returns a copy of a named model input as a {@link Tensor}.
     *
     * @param name input name.
     * @return tensor owned by the caller.
     */
    public Tensor getInput(String name) {
        GenAIRuntime.requireOpen(handle, "Generator");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = GenAIRuntime.createHandle(arena,
                    p -> ort_genai_c_h.OgaGenerator_GetInput(
                            handle, arena.allocateFrom(name), p));
            return Tensor.wrap(out);
        }
    }

    /**
     * Returns a copy of a named model output as a {@link Tensor}.
     *
     * @param name output name.
     * @return tensor owned by the caller.
     */
    public Tensor getOutput(String name) {
        GenAIRuntime.requireOpen(handle, "Generator");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = GenAIRuntime.createHandle(arena,
                    p -> ort_genai_c_h.OgaGenerator_GetOutput(
                            handle, arena.allocateFrom(name), p));
            return Tensor.wrap(out);
        }
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !isDone();
            }

            @Override
            public Integer next() {
                if (isDone()) {
                    throw new NoSuchElementException();
                }
                generateNextToken();
                return getLastToken(0);
            }
        };
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyGenerator(handle);
            handle = MemorySegment.NULL;
        }
    }
}
