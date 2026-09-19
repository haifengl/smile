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
 * Converts text / images / audios into {@link NamedTensors} for multimodal models.
 *
 * @author Haifeng Li
 */
public final class MultiModalProcessor implements AutoCloseable {
    /** Native {@code OgaMultiModalProcessor*} handle. */
    private MemorySegment handle;

    private MultiModalProcessor(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Creates a processor for the given model.
     *
     * @param model multimodal GenAI model.
     * @return processor owned by the caller.
     */
    public static MultiModalProcessor of(Model model) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            return new MultiModalProcessor(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateMultiModalProcessor(model.handle(), out)));
        }
    }

    /**
     * Processes a prompt and optional images into model inputs.
     *
     * @param prompt text prompt.
     * @param images images, or {@code null}.
     * @return named tensors owned by the caller.
     */
    public NamedTensors processImages(String prompt, Images images) {
        GenAIRuntime.requireOpen(handle, "MultiModalProcessor");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment imagesHandle = images == null ? MemorySegment.NULL : images.handle();
            return new NamedTensors(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaProcessorProcessImages(
                            handle, arena.allocateFrom(prompt), imagesHandle, out)));
        }
    }

    /**
     * Processes a prompt and optional audios into model inputs.
     *
     * @param prompt text prompt.
     * @param audios audios, or {@code null}.
     * @return named tensors owned by the caller.
     */
    public NamedTensors processAudios(String prompt, Audios audios) {
        GenAIRuntime.requireOpen(handle, "MultiModalProcessor");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment audiosHandle = audios == null ? MemorySegment.NULL : audios.handle();
            return new NamedTensors(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaProcessorProcessAudios(
                            handle, arena.allocateFrom(prompt), audiosHandle, out)));
        }
    }

    /**
     * Processes a prompt with optional images and audios.
     *
     * @param prompt text prompt.
     * @param images images, or {@code null}.
     * @param audios audios, or {@code null}.
     * @return named tensors owned by the caller.
     */
    public NamedTensors processImagesAndAudios(String prompt, Images images, Audios audios) {
        GenAIRuntime.requireOpen(handle, "MultiModalProcessor");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment imagesHandle = images == null ? MemorySegment.NULL : images.handle();
            MemorySegment audiosHandle = audios == null ? MemorySegment.NULL : audios.handle();
            return new NamedTensors(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaProcessorProcessImagesAndAudios(
                            handle, arena.allocateFrom(prompt), imagesHandle, audiosHandle, out)));
        }
    }

    /**
     * Decodes token ids using the processor's tokenizer.
     *
     * @param tokens token ids.
     * @return decoded text.
     */
    public String decode(int[] tokens) {
        GenAIRuntime.requireOpen(handle, "MultiModalProcessor");
        if (tokens == null) {
            throw new IllegalArgumentException("tokens must not be null");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocateFrom(ValueLayout.JAVA_INT, tokens);
            MemorySegment out = arena.allocate(ort_genai_c_h.C_POINTER);
            GenAIRuntime.checkResult(
                    ort_genai_c_h.OgaProcessorDecode(handle, buf, tokens.length, out));
            return GenAIRuntime.takeString(out.get(ort_genai_c_h.C_POINTER, 0));
        }
    }

    /**
     * Creates a tokenizer stream from this processor.
     *
     * @return stream owned by the caller.
     */
    public TokenizerStream createStream() {
        GenAIRuntime.requireOpen(handle, "MultiModalProcessor");
        try (Arena arena = Arena.ofConfined()) {
            return new TokenizerStream(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateTokenizerStreamFromProcessor(handle, out)));
        }
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyMultiModalProcessor(handle);
            handle = MemorySegment.NULL;
        }
    }
}
