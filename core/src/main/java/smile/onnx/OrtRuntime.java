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
package smile.onnx;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.onnx.foreign.OrtApi;
import smile.onnx.foreign.OrtApiBase;
import smile.onnx.foreign.onnxruntime_c_api_h;

/**
 * Internal helper that bootstraps the ONNX Runtime C API and provides
 * shared utilities (status checking, string reading, etc.) used by all
 * public API classes.
 *
 * <p>The singleton {@code ORT_API} pointer is obtained once at class
 * initialization via {@code OrtGetApiBase()->GetApi(ORT_API_VERSION)}.
 *
 * @author Haifeng Li
 */
final class OrtRuntime {
    /** Current ORT API version requested. */
    private static final int ORT_API_VERSION = 22;

    /** Singleton OrtApi struct pointer (never released). */
    private static final MemorySegment ORT_API;

    static {
        // OrtGetApiBase() returns a const OrtApiBase* (pointer value)
        MemorySegment apiBase = onnxruntime_c_api_h.OrtGetApiBase();
        // Call GetApi(version) function pointer inside OrtApiBase
        MemorySegment getApiFn = OrtApiBase.GetApi(apiBase);
        ORT_API = OrtApiBase.GetApi.invoke(getApiFn, ORT_API_VERSION);
        if (ORT_API == null || ORT_API.equals(MemorySegment.NULL)) {
            throw new IllegalStateException(
                    "OrtGetApiBase()->GetApi(" + ORT_API_VERSION + ") returned NULL. " +
                    "The loaded onnxruntime library may be older than API version " + ORT_API_VERSION + ".");
        }
    }

    /** Not instantiable. */
    private OrtRuntime() {}

    /**
     * Returns the global OrtApi struct pointer.
     * @return the OrtApi pointer.
     */
    static MemorySegment api() {
        return ORT_API;
    }

    /**
     * Checks an {@code OrtStatusPtr} and throws {@link OnnxException} if it
     * is non-null (i.e. represents an error). The status object is released
     * via {@code ReleaseStatus} before the exception is thrown.
     *
     * @param api    the OrtApi struct pointer.
     * @param status the status pointer returned by an ORT call (may be NULL).
     * @throws OnnxException if the status indicates failure.
     */
    static void checkStatus(MemorySegment api, MemorySegment status) {
        if (status == null || status.equals(MemorySegment.NULL)) return;
        int code = OrtApi.GetErrorCode.invoke(OrtApi.GetErrorCode(api), status);
        MemorySegment msgPtr = OrtApi.GetErrorMessage.invoke(OrtApi.GetErrorMessage(api), status);
        String message = msgPtr.reinterpret(Long.MAX_VALUE).getString(0);
        OrtApi.ReleaseStatus.invoke(OrtApi.ReleaseStatus(api), status);
        throw new OnnxException(code, message);
    }

    /**
     * Reads a null-terminated C string from a {@code MemorySegment} pointer.
     *
     * @param ptr pointer to the first byte of the string.
     * @return the Java String.
     */
    static String readString(MemorySegment ptr) {
        return ptr.reinterpret(Long.MAX_VALUE).getString(0);
    }

    /**
     * Allocates a string pointer (char**) in the given arena, calls the
     * supplied ORT function that writes a heap-allocated string to it, reads
     * the result, and frees the native string via the default allocator.
     *
     * <p>This pattern is used by {@code SessionGetInputName},
     * {@code ModelMetadataGetProducerName}, etc.
     *
     * @param api      the OrtApi struct.
     * @param arena    the arena used for the temporary pointer-to-pointer.
     * @param fn       a callback that accepts the OrtAllocator* and
     *                 the char** output buffer and returns an OrtStatusPtr.
     * @return the resulting Java String.
     */
    static String allocatorGetString(MemorySegment api, Arena arena,
                                     java.util.function.BiFunction<MemorySegment, MemorySegment, MemorySegment> fn) {
        // Get the default (CPU) allocator
        MemorySegment pAlloc = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        MemorySegment st = OrtApi.GetAllocatorWithDefaultOptions.invoke(
                OrtApi.GetAllocatorWithDefaultOptions(api), pAlloc);
        checkStatus(api, st);
        MemorySegment allocator = pAlloc.get(onnxruntime_c_api_h.C_POINTER, 0);

        MemorySegment pStr = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        MemorySegment status = fn.apply(allocator, pStr);
        checkStatus(api, status);
        MemorySegment strPtr = pStr.get(onnxruntime_c_api_h.C_POINTER, 0);
        String result = readString(strPtr);
        // Free the string via the allocator
        OrtApi.AllocatorFree.invoke(OrtApi.AllocatorFree(api), allocator, strPtr);
        return result;
    }
}

