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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import smile.onnx.genai.foreign.ort_genai_c_h;

/**
 * Thin GenAI tensor wrapper for {@link Generator#setModelInput} / multimodal inputs.
 *
 * <p>Element-type ordinals match {@code OgaElementType} / ONNX tensor element types.
 *
 * @author Haifeng Li
 */
public final class Tensor implements AutoCloseable {
    /** Element types matching {@code OgaElementType}. */
    public enum ElementType {
        /** Undefined. */ UNDEFINED,
        /** float32. */ FLOAT32,
        /** uint8. */ UINT8,
        /** int8. */ INT8,
        /** uint16. */ UINT16,
        /** int16. */ INT16,
        /** int32. */ INT32,
        /** int64. */ INT64,
        /** string. */ STRING,
        /** bool. */ BOOL,
        /** float16. */ FLOAT16,
        /** float64. */ FLOAT64,
        /** uint32. */ UINT32,
        /** uint64. */ UINT64,
        /** complex64. */ COMPLEX64,
        /** complex128. */ COMPLEX128,
        /** bfloat16. */ BFLOAT16
    }

    /** Native {@code OgaTensor*} handle. */
    private MemorySegment handle;
    /** Keeps the backing buffer reachable for tensors created from Java memory. */
    private final ByteBuffer dataBuffer;
    /** Element type. */
    private final ElementType elementType;
    /** Shape. */
    private final long[] shape;

    private Tensor(MemorySegment handle, ByteBuffer dataBuffer,
                   ElementType elementType, long[] shape) {
        this.handle = handle;
        this.dataBuffer = dataBuffer;
        this.elementType = elementType;
        this.shape = shape != null ? shape.clone() : new long[0];
    }

    /**
     * Wraps an existing native tensor handle (takes ownership).
     *
     * @param handle native handle.
     * @return tensor owned by the caller.
     */
    static Tensor wrap(MemorySegment handle) {
        ElementType type = ElementType.UNDEFINED;
        long[] shape = new long[0];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment typeOut = arena.allocate(ValueLayout.JAVA_INT);
            GenAIRuntime.checkResult(ort_genai_c_h.OgaTensorGetType(handle, typeOut));
            int ordinal = typeOut.get(ValueLayout.JAVA_INT, 0);
            ElementType[] values = ElementType.values();
            if (ordinal >= 0 && ordinal < values.length) {
                type = values[ordinal];
            }
            MemorySegment rankOut = arena.allocate(ValueLayout.JAVA_LONG);
            GenAIRuntime.checkResult(ort_genai_c_h.OgaTensorGetShapeRank(handle, rankOut));
            long rank = GenAIRuntime.getSizeT(rankOut);
            if (rank > 0) {
                MemorySegment dims = arena.allocate(ValueLayout.JAVA_LONG, rank);
                GenAIRuntime.checkResult(ort_genai_c_h.OgaTensorGetShape(handle, dims, rank));
                shape = new long[(int) rank];
                for (int i = 0; i < rank; i++) {
                    shape[i] = dims.getAtIndex(ValueLayout.JAVA_LONG, i);
                }
            }
        }
        return new Tensor(handle, null, type, shape);
    }

    /**
     * Creates a tensor from a direct native-order {@link ByteBuffer}.
     *
     * @param data        direct buffer with native byte order; kept reachable.
     * @param shape       tensor shape.
     * @param elementType element type.
     * @return tensor owned by the caller.
     */
    public static Tensor of(ByteBuffer data, long[] shape, ElementType elementType) {
        if (data == null || shape == null) {
            throw new IllegalArgumentException("data and shape must not be null");
        }
        if (elementType == null || elementType == ElementType.UNDEFINED) {
            throw new IllegalArgumentException("elementType must be defined");
        }
        if (!data.isDirect()) {
            throw new IllegalArgumentException("Tensor data must be a direct ByteBuffer");
        }
        if (data.order() != ByteOrder.nativeOrder()) {
            throw new IllegalArgumentException("Tensor data must have native byte order");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dataSeg = MemorySegment.ofBuffer(data);
            MemorySegment shapeSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, shape);
            MemorySegment handle = GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateTensorFromBuffer(
                            dataSeg, shapeSeg, shape.length, elementType.ordinal(), out));
            return new Tensor(handle, data, elementType, shape);
        }
    }

    /**
     * Returns the element type.
     *
     * @return element type.
     */
    public ElementType type() {
        return elementType;
    }

    /**
     * Returns a copy of the shape.
     *
     * @return shape.
     */
    public long[] shape() {
        return shape.clone();
    }

    /** @return the native handle (package use). */
    MemorySegment handle() {
        GenAIRuntime.requireOpen(handle, "Tensor");
        return handle;
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyTensor(handle);
            handle = MemorySegment.NULL;
        }
    }
}
