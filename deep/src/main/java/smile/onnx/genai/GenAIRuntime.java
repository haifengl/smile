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
 * Package-private helpers for checking GenAI {@code OgaResult*} status codes
 * and converting native strings.
 *
 * @author Haifeng Li
 */
final class GenAIRuntime {
    /** Not instantiable. */
    private GenAIRuntime() {}

    /**
     * Ensures the native GenAI library is loadable. Touching
     * {@link ort_genai_c_h} triggers {@code SymbolLookup.libraryLookup}.
     */
    static void ensureLoaded() {
        // Force class initialization so missing natives fail with a clear message.
        MemorySegment unused = ort_genai_c_h.OgaSetTelemetryEnabled$address();
        if (unused == null) {
            throw new GenAIException("onnxruntime-genai native library failed to load");
        }
    }

    /**
     * Checks an {@code OgaResult*} and throws {@link GenAIException} if it is
     * non-null. The result object is destroyed before the exception is thrown.
     *
     * @param result the result pointer returned by a GenAI call (may be NULL).
     * @throws GenAIException if the result indicates failure.
     */
    static void checkResult(MemorySegment result) {
        if (result == null || result.equals(MemorySegment.NULL)) {
            return;
        }
        String message;
        try {
            MemorySegment msgPtr = ort_genai_c_h.OgaResultGetError(result);
            message = readString(msgPtr);
        } catch (Throwable t) {
            message = "ONNX Runtime GenAI call failed (unable to read error message)";
            ort_genai_c_h.OgaDestroyResult(result);
            throw new GenAIException(message, t);
        }
        ort_genai_c_h.OgaDestroyResult(result);
        throw new GenAIException(message == null || message.isEmpty()
                ? "ONNX Runtime GenAI call failed"
                : message);
    }

    /**
     * Reads a null-terminated C string from a {@code MemorySegment} pointer.
     *
     * @param ptr pointer to the first byte of the string; must not be NULL.
     * @return the Java string.
     */
    static String readString(MemorySegment ptr) {
        if (ptr == null || ptr.equals(MemorySegment.NULL)) {
            return "";
        }
        return ptr.reinterpret(Long.MAX_VALUE).getString(0);
    }

    /**
     * Reads a GenAI-owned C string and frees it with {@code OgaDestroyString}.
     *
     * @param ptr pointer returned by a GenAI API that transfers string ownership.
     * @return the Java string (empty when {@code ptr} is NULL).
     */
    static String takeString(MemorySegment ptr) {
        if (ptr == null || ptr.equals(MemorySegment.NULL)) {
            return "";
        }
        try {
            return readString(ptr);
        } finally {
            ort_genai_c_h.OgaDestroyString(ptr);
        }
    }

    /**
     * Allocates a pointer slot, invokes a create-style GenAI call that writes
     * an opaque handle into it, checks the result, and returns the handle.
     *
     * @param arena temporary arena for the out-pointer.
     * @param call  callback that receives {@code T**} and returns {@code OgaResult*}.
     * @return the created native handle.
     */
    static MemorySegment createHandle(Arena arena,
                                      java.util.function.Function<MemorySegment, MemorySegment> call) {
        MemorySegment out = arena.allocate(ort_genai_c_h.C_POINTER);
        out.set(ort_genai_c_h.C_POINTER, 0, MemorySegment.NULL);
        checkResult(call.apply(out));
        MemorySegment handle = out.get(ort_genai_c_h.C_POINTER, 0);
        if (handle == null || handle.equals(MemorySegment.NULL)) {
            throw new GenAIException("GenAI create returned a NULL handle");
        }
        return handle;
    }

    /**
     * Reads a {@code size_t*} out-parameter after a successful GenAI call.
     *
     * @param out pointer allocated with {@link ort_genai_c_h#C_LONG_LONG}.
     * @return the size value.
     */
    static long getSizeT(MemorySegment out) {
        return out.get(ValueLayout.JAVA_LONG, 0);
    }

    /**
     * Throws if {@code handle} has already been closed.
     *
     * @param handle native handle.
     * @param type   human-readable type name for the error message.
     */
    static void requireOpen(MemorySegment handle, String type) {
        if (handle == null || handle.equals(MemorySegment.NULL)) {
            throw new IllegalStateException(type + " has been closed");
        }
    }
}
