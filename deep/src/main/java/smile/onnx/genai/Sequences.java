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
import smile.onnx.genai.foreign.ort_genai_c_h;

/**
 * A collection of token-id sequences (batch of prompts / responses).
 *
 * @author Haifeng Li
 */
public final class Sequences implements AutoCloseable {
    /** Native {@code OgaSequences*} handle. */
    private MemorySegment handle;

    Sequences(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Creates an empty sequences container.
     *
     * @return a new sequences object owned by the caller.
     */
    public static Sequences create() {
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            return new Sequences(GenAIRuntime.createHandle(arena, ort_genai_c_h::OgaCreateSequences));
        }
    }

    /**
     * Appends a token-id sequence as a new batch entry.
     *
     * @param tokens token ids.
     * @return {@code this} for chaining.
     */
    public Sequences append(int[] tokens) {
        GenAIRuntime.requireOpen(handle, "Sequences");
        if (tokens == null) {
            throw new IllegalArgumentException("tokens must not be null");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocateFrom(ValueLayout.JAVA_INT, tokens);
            GenAIRuntime.checkResult(
                    ort_genai_c_h.OgaAppendTokenSequence(buf, tokens.length, handle));
        }
        return this;
    }

    /**
     * Appends a single token to an existing sequence.
     *
     * @param token         token id.
     * @param sequenceIndex sequence index (0-based).
     * @return {@code this} for chaining.
     */
    public Sequences appendToken(int token, long sequenceIndex) {
        GenAIRuntime.requireOpen(handle, "Sequences");
        GenAIRuntime.checkResult(
                ort_genai_c_h.OgaAppendTokenToSequence(token, handle, sequenceIndex));
        return this;
    }

    /**
     * Returns the number of sequences (batch size).
     *
     * @return sequence count.
     */
    public long count() {
        GenAIRuntime.requireOpen(handle, "Sequences");
        return ort_genai_c_h.OgaSequencesCount(handle);
    }

    /**
     * Returns the token ids for the given sequence index.
     *
     * @param sequenceIndex 0-based sequence index.
     * @return a copy of the token ids.
     */
    public int[] get(long sequenceIndex) {
        GenAIRuntime.requireOpen(handle, "Sequences");
        long n = ort_genai_c_h.OgaSequencesGetSequenceCount(handle, sequenceIndex);
        if (n <= 0) {
            return new int[0];
        }
        MemorySegment data = ort_genai_c_h.OgaSequencesGetSequenceData(handle, sequenceIndex);
        if (data == null || data.equals(MemorySegment.NULL)) {
            throw new GenAIException("OgaSequencesGetSequenceData returned NULL");
        }
        return data.reinterpret(n * ValueLayout.JAVA_INT.byteSize())
                .toArray(ValueLayout.JAVA_INT);
    }

    /** @return the native handle (package use). */
    MemorySegment handle() {
        GenAIRuntime.requireOpen(handle, "Sequences");
        return handle;
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroySequences(handle);
            handle = MemorySegment.NULL;
        }
    }
}
