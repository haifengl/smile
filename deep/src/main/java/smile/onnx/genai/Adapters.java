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
 * Container for LoRA adapters that can be activated on a {@link Generator}.
 *
 * @author Haifeng Li
 */
public final class Adapters implements AutoCloseable {
    /** Native {@code OgaAdapters*} handle. */
    private MemorySegment handle;

    private Adapters(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Creates an adapters container for the given model.
     *
     * @param model the model.
     * @return adapters owned by the caller.
     */
    public static Adapters of(Model model) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            return new Adapters(GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateAdapters(model.handle(), out)));
        }
    }

    /**
     * Loads an adapter from disk under a caller-chosen name.
     *
     * @param adapterFilePath path to the adapter file.
     * @param adapterName     unique name used later with {@link Generator#setActiveAdapter}.
     * @return {@code this} for chaining.
     */
    public Adapters load(String adapterFilePath, String adapterName) {
        GenAIRuntime.requireOpen(handle, "Adapters");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaLoadAdapter(
                    handle,
                    arena.allocateFrom(adapterFilePath),
                    arena.allocateFrom(adapterName)));
        }
        return this;
    }

    /**
     * Unloads a previously loaded adapter.
     *
     * @param adapterName adapter name.
     * @return {@code this} for chaining.
     */
    public Adapters unload(String adapterName) {
        GenAIRuntime.requireOpen(handle, "Adapters");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaUnloadAdapter(
                    handle, arena.allocateFrom(adapterName)));
        }
        return this;
    }

    /** @return the native handle (package use). */
    MemorySegment handle() {
        GenAIRuntime.requireOpen(handle, "Adapters");
        return handle;
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyAdapters(handle);
            handle = MemorySegment.NULL;
        }
    }
}
