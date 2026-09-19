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
 * Named collection of tensors matching model input names.
 *
 * @author Haifeng Li
 */
public final class NamedTensors implements AutoCloseable {
    /** Native {@code OgaNamedTensors*} handle. */
    private MemorySegment handle;

    NamedTensors(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Creates an empty named-tensor container.
     *
     * @return a new container owned by the caller.
     */
    public static NamedTensors create() {
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            return new NamedTensors(GenAIRuntime.createHandle(arena, ort_genai_c_h::OgaCreateNamedTensors));
        }
    }

    /**
     * Sets a named tensor (takes a reference; the tensor must outlive use).
     *
     * @param name   input name.
     * @param tensor tensor value.
     * @return {@code this} for chaining.
     */
    public NamedTensors set(String name, Tensor tensor) {
        GenAIRuntime.requireOpen(handle, "NamedTensors");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaNamedTensorsSet(
                    handle, arena.allocateFrom(name), tensor.handle()));
        }
        return this;
    }

    /**
     * Returns a copy of the named tensor.
     *
     * @param name input name.
     * @return tensor owned by the caller.
     */
    public Tensor get(String name) {
        GenAIRuntime.requireOpen(handle, "NamedTensors");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = GenAIRuntime.createHandle(arena,
                    p -> ort_genai_c_h.OgaNamedTensorsGet(handle, arena.allocateFrom(name), p));
            return Tensor.wrap(out);
        }
    }

    /**
     * Deletes a named tensor entry.
     *
     * @param name input name.
     * @return {@code this} for chaining.
     */
    public NamedTensors delete(String name) {
        GenAIRuntime.requireOpen(handle, "NamedTensors");
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(
                    ort_genai_c_h.OgaNamedTensorsDelete(handle, arena.allocateFrom(name)));
        }
        return this;
    }

    /**
     * Returns the number of named tensors.
     *
     * @return count.
     */
    public long count() {
        GenAIRuntime.requireOpen(handle, "NamedTensors");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ValueLayout.JAVA_LONG);
            GenAIRuntime.checkResult(ort_genai_c_h.OgaNamedTensorsCount(handle, out));
            return GenAIRuntime.getSizeT(out);
        }
    }

    /**
     * Returns the tensor names.
     *
     * @return names.
     */
    public String[] names() {
        GenAIRuntime.requireOpen(handle, "NamedTensors");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array = GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaNamedTensorsGetNames(handle, out));
            try {
                MemorySegment countOut = arena.allocate(ValueLayout.JAVA_LONG);
                GenAIRuntime.checkResult(ort_genai_c_h.OgaStringArrayGetCount(array, countOut));
                long n = GenAIRuntime.getSizeT(countOut);
                String[] names = new String[(int) n];
                for (long i = 0; i < n; i++) {
                    MemorySegment strOut = arena.allocate(ort_genai_c_h.C_POINTER);
                    GenAIRuntime.checkResult(ort_genai_c_h.OgaStringArrayGetString(array, i, strOut));
                    names[(int) i] = GenAIRuntime.readString(strOut.get(ort_genai_c_h.C_POINTER, 0));
                }
                return names;
            } finally {
                ort_genai_c_h.OgaDestroyStringArray(array);
            }
        }
    }

    /** @return the native handle (package use). */
    MemorySegment handle() {
        GenAIRuntime.requireOpen(handle, "NamedTensors");
        return handle;
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyNamedTensors(handle);
            handle = MemorySegment.NULL;
        }
    }
}
