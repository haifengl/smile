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
import smile.onnx.genai.foreign.ort_genai_c_h;

/**
 * Search and sampling options for a {@link Generator}.
 *
 * <p>Common search option names include {@code max_length}, {@code temperature},
 * {@code top_p}, {@code top_k}, {@code do_sample}, and {@code repetition_penalty}.
 *
 * @author Haifeng Li
 */
public final class GeneratorParams implements AutoCloseable {
    /** Native {@code OgaGeneratorParams*} handle. */
    private MemorySegment handle;

    GeneratorParams(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Creates generator parameters for a model.
     *
     * @param model the model.
     * @return a new params object owned by the caller.
     */
    public static GeneratorParams of(Model model) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            return new GeneratorParams(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateGeneratorParams(model.handle(), out)));
        }
    }

    /**
     * Sets a numeric search option.
     *
     * @param name  option name.
     * @param value option value.
     * @return {@code this} for chaining.
     */
    public GeneratorParams setSearchOption(String name, double value) {
        GenAIRuntime.requireOpen(handle, "GeneratorParams");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaGeneratorParamsSetSearchNumber(
                    handle, arena.allocateFrom(name), value));
        }
        return this;
    }

    /**
     * Sets a boolean search option.
     *
     * @param name  option name.
     * @param value option value.
     * @return {@code this} for chaining.
     */
    public GeneratorParams setSearchOption(String name, boolean value) {
        GenAIRuntime.requireOpen(handle, "GeneratorParams");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaGeneratorParamsSetSearchBool(
                    handle, arena.allocateFrom(name), value));
        }
        return this;
    }

    /**
     * Sets guidance (e.g. JSON schema / grammar) for constrained decoding.
     *
     * @param type           guidance type.
     * @param data           guidance payload.
     * @param enableFfTokens whether to enable feed-forward tokens.
     * @return {@code this} for chaining.
     */
    public GeneratorParams setGuidance(String type, String data, boolean enableFfTokens) {
        GenAIRuntime.requireOpen(handle, "GeneratorParams");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaGeneratorParamsSetGuidance(
                    handle,
                    arena.allocateFrom(type),
                    arena.allocateFrom(data),
                    enableFfTokens));
        }
        return this;
    }

    /**
     * Sets a numeric speculative-decoding option.
     *
     * @param name  option name.
     * @param value option value.
     * @return {@code this} for chaining.
     */
    public GeneratorParams setSpeculativeOption(String name, double value) {
        GenAIRuntime.requireOpen(handle, "GeneratorParams");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaGeneratorParamsSetSpeculativeNumber(
                    handle, arena.allocateFrom(name), value));
        }
        return this;
    }

    /**
     * Sets a boolean speculative-decoding option.
     *
     * @param name  option name.
     * @param value option value.
     * @return {@code this} for chaining.
     */
    public GeneratorParams setSpeculativeOption(String name, boolean value) {
        GenAIRuntime.requireOpen(handle, "GeneratorParams");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaGeneratorParamsSetSpeculativeBool(
                    handle, arena.allocateFrom(name), value));
        }
        return this;
    }

    /** @return the native handle (package use). */
    MemorySegment handle() {
        GenAIRuntime.requireOpen(handle, "GeneratorParams");
        return handle;
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyGeneratorParams(handle);
            handle = MemorySegment.NULL;
        }
    }
}
