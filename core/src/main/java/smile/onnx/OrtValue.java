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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import smile.onnx.foreign.OrtApi;
import smile.onnx.foreign.onnxruntime_c_api_h;

/**
 * A wrapper around an OrtValue — the fundamental data container in ONNX
 * Runtime. An {@code OrtValue} may hold a dense tensor, a sparse tensor,
 * a sequence, or a map, depending on the ONNX model's input/output types.
 *
 * <p>Instances of this class must be closed after use. Factory methods
 * support creation from Java primitive arrays; data is copied into a
 * native memory segment owned by this object.
 *
 * <pre>{@code
 * float[] data = { 1f, 2f, 3f, 4f };
 * long[] shape = { 1, 4 };
 * try (OrtValue input = OrtValue.fromFloatArray(data, shape)) {
 *     OrtValue[] outputs = session.run(inputs, outputNames);
 *     float[] result = outputs[0].toFloatArray();
 * }
 * }</pre>
 *
 * @author Haifeng Li
 */
public class OrtValue implements AutoCloseable {
    /** The ORT API pointer. */
    private final MemorySegment api;
    /** Arena that owns the native tensor data memory. */
    private final Arena arena;
    /** The native OrtValue pointer. */
    final MemorySegment handle;
    /** Whether this instance owns the handle (and must release it). */
    private final boolean owner;

    /**
     * Wraps an existing OrtValue handle. Used internally when ORT allocates
     * the OrtValue (e.g. as a session output).
     *
     * @param handle the native OrtValue pointer.
     * @param owner  if true, {@link #close()} will call ReleaseValue.
     */
    OrtValue(MemorySegment handle, boolean owner) {
        this.api = OrtRuntime.api();
        this.arena = null;
        this.handle = handle;
        this.owner = owner;
    }

    /**
     * Private constructor used by factory methods that allocate tensor data.
     */
    private OrtValue(MemorySegment handle, Arena arena) {
        this.api = OrtRuntime.api();
        this.arena = arena;
        this.handle = handle;
        this.owner = true;
    }

    // -----------------------------------------------------------------------
    // Factory methods — tensor creation from Java arrays
    // -----------------------------------------------------------------------

    /**
     * Creates an OrtValue tensor backed by a copy of the given {@code float[]}
     * array.
     *
     * @param data  the float values.
     * @param shape the tensor dimensions; the product must equal
     *              {@code data.length}.
     * @return a new OrtValue owning the tensor data.
     */
    public static OrtValue fromFloatArray(float[] data, long[] shape) {
        Arena arena = Arena.ofConfined();
        MemorySegment mem = arena.allocate(MemoryLayout.sequenceLayout(data.length, ValueLayout.JAVA_FLOAT));
        FloatBuffer buf = mem.asByteBuffer().asFloatBuffer();
        buf.put(data);
        return createWithData(arena, mem, (long) data.length * Float.BYTES, shape, ElementType.FLOAT);
    }

    /**
     * Creates an OrtValue tensor backed by a copy of the given {@code double[]}
     * array.
     *
     * @param data  the double values.
     * @param shape the tensor dimensions.
     * @return a new OrtValue owning the tensor data.
     */
    public static OrtValue fromDoubleArray(double[] data, long[] shape) {
        Arena arena = Arena.ofConfined();
        MemorySegment mem = arena.allocate(MemoryLayout.sequenceLayout(data.length, ValueLayout.JAVA_DOUBLE));
        DoubleBuffer buf = mem.asByteBuffer().asDoubleBuffer();
        buf.put(data);
        return createWithData(arena, mem, (long) data.length * Double.BYTES, shape, ElementType.DOUBLE);
    }

    /**
     * Creates an OrtValue tensor backed by a copy of the given {@code int[]}
     * array.
     *
     * @param data  the int32 values.
     * @param shape the tensor dimensions.
     * @return a new OrtValue owning the tensor data.
     */
    public static OrtValue fromIntArray(int[] data, long[] shape) {
        Arena arena = Arena.ofConfined();
        MemorySegment mem = arena.allocate(MemoryLayout.sequenceLayout(data.length, ValueLayout.JAVA_INT));
        IntBuffer buf = mem.asByteBuffer().asIntBuffer();
        buf.put(data);
        return createWithData(arena, mem, (long) data.length * Integer.BYTES, shape, ElementType.INT32);
    }

    /**
     * Creates an OrtValue tensor backed by a copy of the given {@code long[]}
     * array.
     *
     * @param data  the int64 values.
     * @param shape the tensor dimensions.
     * @return a new OrtValue owning the tensor data.
     */
    public static OrtValue fromLongArray(long[] data, long[] shape) {
        Arena arena = Arena.ofConfined();
        MemorySegment mem = arena.allocate(MemoryLayout.sequenceLayout(data.length, ValueLayout.JAVA_LONG));
        LongBuffer buf = mem.asByteBuffer().asLongBuffer();
        buf.put(data);
        return createWithData(arena, mem, (long) data.length * Long.BYTES, shape, ElementType.INT64);
    }

    /**
     * Creates an OrtValue tensor backed by a copy of the given {@code byte[]}
     * array (INT8 element type).
     *
     * @param data  the int8 values.
     * @param shape the tensor dimensions.
     * @return a new OrtValue owning the tensor data.
     */
    public static OrtValue fromByteArray(byte[] data, long[] shape) {
        Arena arena = Arena.ofConfined();
        MemorySegment mem = arena.allocate(data.length);
        mem.asByteBuffer().put(data);
        return createWithData(arena, mem, data.length, shape, ElementType.INT8);
    }

    /**
     * Creates an OrtValue tensor backed by a copy of the given {@code boolean[]}
     * array (BOOL element type, stored as bytes).
     *
     * @param data  the boolean values.
     * @param shape the tensor dimensions.
     * @return a new OrtValue owning the tensor data.
     */
    public static OrtValue fromBooleanArray(boolean[] data, long[] shape) {
        Arena arena = Arena.ofConfined();
        MemorySegment mem = arena.allocate(data.length);
        for (int i = 0; i < data.length; i++) {
            mem.set(ValueLayout.JAVA_BYTE, i, data[i] ? (byte) 1 : (byte) 0);
        }
        return createWithData(arena, mem, data.length, shape, ElementType.BOOL);
    }

    /** Internal factory that calls CreateTensorWithDataAsOrtValue. */
    private static OrtValue createWithData(Arena arena, MemorySegment data, long dataBytes,
                                           long[] shape, ElementType elementType) {
        MemorySegment api = OrtRuntime.api();

        // Build CPU memory info
        MemorySegment pMemInfo = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        // OrtMemTypeDefault=0, OrtDeviceAllocator=0
        MemorySegment st = OrtApi.CreateCpuMemoryInfo.invoke(
                OrtApi.CreateCpuMemoryInfo(api),
                /*OrtArenaAllocator=*/1, /*OrtMemTypeDefault=*/0, pMemInfo);
        OrtRuntime.checkStatus(api, st);
        MemorySegment memInfo = pMemInfo.get(onnxruntime_c_api_h.C_POINTER, 0);

        // Build native long[] for shape
        MemorySegment shapeSeg = arena.allocate(
                MemoryLayout.sequenceLayout(shape.length, ValueLayout.JAVA_LONG));
        for (int i = 0; i < shape.length; i++) {
            shapeSeg.setAtIndex(ValueLayout.JAVA_LONG, i, shape[i]);
        }

        // CreateTensorWithDataAsOrtValue(memInfo, data, dataBytes, shape, ndim, elemType, out)
        MemorySegment pValue = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        st = OrtApi.CreateTensorWithDataAsOrtValue.invoke(
                OrtApi.CreateTensorWithDataAsOrtValue(api),
                memInfo, data, dataBytes, shapeSeg, shape.length,
                elementType.value(), pValue);
        OrtRuntime.checkStatus(api, st);

        OrtApi.ReleaseMemoryInfo.invoke(OrtApi.ReleaseMemoryInfo(api), memInfo);

        MemorySegment handle = pValue.get(onnxruntime_c_api_h.C_POINTER, 0);
        return new OrtValue(handle, arena);
    }

    // -----------------------------------------------------------------------
    // Type and shape queries
    // -----------------------------------------------------------------------

    /**
     * Returns the ONNX value type of this OrtValue.
     *
     * @return the {@link OnnxType}.
     */
    public OnnxType onnxType() {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment pType = a.allocate(onnxruntime_c_api_h.C_INT);
            MemorySegment st = OrtApi.GetValueType.invoke(OrtApi.GetValueType(api), handle, pType);
            OrtRuntime.checkStatus(api, st);
            return OnnxType.of(pType.get(ValueLayout.JAVA_INT, 0));
        }
    }

    /**
     * Returns {@code true} if this OrtValue is a (dense) tensor.
     *
     * @return true for tensors.
     */
    public boolean isTensor() {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment pFlag = a.allocate(onnxruntime_c_api_h.C_INT);
            MemorySegment st = OrtApi.IsTensor.invoke(OrtApi.IsTensor(api), handle, pFlag);
            OrtRuntime.checkStatus(api, st);
            return pFlag.get(ValueLayout.JAVA_INT, 0) != 0;
        }
    }

    /**
     * Returns the type and shape info for this tensor.
     *
     * @return the {@link TensorInfo}.
     * @throws OnnxException if this value is not a tensor.
     */
    public TensorInfo tensorInfo() {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment pTsi = a.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st = OrtApi.GetTensorTypeAndShape.invoke(
                    OrtApi.GetTensorTypeAndShape(api), handle, pTsi);
            OrtRuntime.checkStatus(api, st);
            MemorySegment tsi = pTsi.get(onnxruntime_c_api_h.C_POINTER, 0);
            try {
                return readTensorInfo(api, a, tsi);
            } finally {
                OrtApi.ReleaseTensorTypeAndShapeInfo.invoke(
                        OrtApi.ReleaseTensorTypeAndShapeInfo(api), tsi);
            }
        }
    }

    /** Reads ElementType + shape from a OrtTensorTypeAndShapeInfo pointer. */
    static TensorInfo readTensorInfo(MemorySegment api, Arena arena, MemorySegment tsi) {
        // Element type
        MemorySegment pElemType = arena.allocate(onnxruntime_c_api_h.C_INT);
        MemorySegment st = OrtApi.GetTensorElementType.invoke(
                OrtApi.GetTensorElementType(api), tsi, pElemType);
        OrtRuntime.checkStatus(api, st);
        ElementType elemType = ElementType.of(pElemType.get(ValueLayout.JAVA_INT, 0));

        // Rank
        MemorySegment pRank = arena.allocate(onnxruntime_c_api_h.C_POINTER); // size_t*
        st = OrtApi.GetDimensionsCount.invoke(OrtApi.GetDimensionsCount(api), tsi, pRank);
        OrtRuntime.checkStatus(api, st);
        long rank = pRank.get(ValueLayout.JAVA_LONG, 0);

        // Dimensions
        MemorySegment dimsSeg = arena.allocate(
                MemoryLayout.sequenceLayout(rank, ValueLayout.JAVA_LONG));
        st = OrtApi.GetDimensions.invoke(OrtApi.GetDimensions(api), tsi, dimsSeg, rank);
        OrtRuntime.checkStatus(api, st);
        long[] shape = new long[(int) rank];
        for (int i = 0; i < rank; i++) {
            shape[i] = dimsSeg.getAtIndex(ValueLayout.JAVA_LONG, i);
        }

        return new TensorInfo(elemType, shape);
    }

    // -----------------------------------------------------------------------
    // Data extraction
    // -----------------------------------------------------------------------

    /**
     * Copies the tensor data into a new {@code float[]} array.
     * The tensor must have element type {@link ElementType#FLOAT}.
     *
     * @return the float data.
     */
    public float[] toFloatArray() {
        TensorInfo info = tensorInfo();
        long count = info.elementCount();
        MemorySegment dataPtr = getMutableDataPointer();
        MemorySegment seg = dataPtr.reinterpret(count * Float.BYTES);
        float[] result = new float[(int) count];
        FloatBuffer buf = seg.asByteBuffer().asFloatBuffer();
        buf.get(result);
        return result;
    }

    /**
     * Copies the tensor data into a new {@code double[]} array.
     * The tensor must have element type {@link ElementType#DOUBLE}.
     *
     * @return the double data.
     */
    public double[] toDoubleArray() {
        TensorInfo info = tensorInfo();
        long count = info.elementCount();
        MemorySegment dataPtr = getMutableDataPointer();
        MemorySegment seg = dataPtr.reinterpret(count * Double.BYTES);
        double[] result = new double[(int) count];
        DoubleBuffer buf = seg.asByteBuffer().asDoubleBuffer();
        buf.get(result);
        return result;
    }

    /**
     * Copies the tensor data into a new {@code int[]} array.
     * The tensor must have element type {@link ElementType#INT32}.
     *
     * @return the int data.
     */
    public int[] toIntArray() {
        TensorInfo info = tensorInfo();
        long count = info.elementCount();
        MemorySegment dataPtr = getMutableDataPointer();
        MemorySegment seg = dataPtr.reinterpret(count * Integer.BYTES);
        int[] result = new int[(int) count];
        IntBuffer buf = seg.asByteBuffer().asIntBuffer();
        buf.get(result);
        return result;
    }

    /**
     * Copies the tensor data into a new {@code long[]} array.
     * The tensor must have element type {@link ElementType#INT64}.
     *
     * @return the long data.
     */
    public long[] toLongArray() {
        TensorInfo info = tensorInfo();
        long count = info.elementCount();
        MemorySegment dataPtr = getMutableDataPointer();
        MemorySegment seg = dataPtr.reinterpret(count * Long.BYTES);
        long[] result = new long[(int) count];
        LongBuffer buf = seg.asByteBuffer().asLongBuffer();
        buf.get(result);
        return result;
    }

    /**
     * Copies the tensor data into a new {@code byte[]} array.
     * The tensor must have element type {@link ElementType#INT8} or
     * {@link ElementType#UINT8}.
     *
     * @return the byte data.
     */
    public byte[] toByteArray() {
        TensorInfo info = tensorInfo();
        long count = info.elementCount();
        MemorySegment dataPtr = getMutableDataPointer();
        MemorySegment seg = dataPtr.reinterpret(count);
        byte[] result = new byte[(int) count];
        seg.asByteBuffer().get(result);
        return result;
    }

    /**
     * Copies the string tensor data into a {@code String[]} array.
     * The tensor must have element type {@link ElementType#STRING}.
     *
     * @return the string data.
     */
    public String[] toStringArray() {
        TensorInfo info = tensorInfo();
        long count = info.elementCount();
        try (Arena a = Arena.ofConfined()) {
            // Get required buffer size
            MemorySegment pLen = a.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st = OrtApi.GetStringTensorDataLength.invoke(
                    OrtApi.GetStringTensorDataLength(api), handle, pLen);
            OrtRuntime.checkStatus(api, st);
            long totalLen = pLen.get(ValueLayout.JAVA_LONG, 0);

            // Buffer for all string bytes and offsets
            MemorySegment strBuf = a.allocate(totalLen + 1);
            MemorySegment offsetsBuf = a.allocate(
                    MemoryLayout.sequenceLayout(count, ValueLayout.JAVA_LONG));

            st = OrtApi.GetStringTensorContent.invoke(
                    OrtApi.GetStringTensorContent(api),
                    handle, strBuf, totalLen, offsetsBuf, count);
            OrtRuntime.checkStatus(api, st);

            String[] result = new String[(int) count];
            for (int i = 0; i < count; i++) {
                long offset = offsetsBuf.getAtIndex(ValueLayout.JAVA_LONG, i);
                long end = (i + 1 < count)
                        ? offsetsBuf.getAtIndex(ValueLayout.JAVA_LONG, i + 1)
                        : totalLen;
                byte[] bytes = new byte[(int) (end - offset)];
                MemorySegment.copy(strBuf, ValueLayout.JAVA_BYTE, offset,
                        MemorySegment.ofArray(bytes), ValueLayout.JAVA_BYTE, 0, bytes.length);
                result[i] = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
            return result;
        }
    }

    /** Returns a pointer to the raw tensor data buffer via GetTensorMutableData. */
    private MemorySegment getMutableDataPointer() {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment pData = a.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st = OrtApi.GetTensorMutableData.invoke(
                    OrtApi.GetTensorMutableData(api), handle, pData);
            OrtRuntime.checkStatus(api, st);
            return pData.get(onnxruntime_c_api_h.C_POINTER, 0);
        }
    }

    @Override
    public void close() {
        if (owner) {
            OrtApi.ReleaseValue.invoke(OrtApi.ReleaseValue(api), handle);
        }
        if (arena != null) {
            arena.close();
        }
    }

    @Override
    public String toString() {
        try {
            return "OrtValue{type=" + onnxType() + ", tensorInfo=" + tensorInfo() + "}";
        } catch (Exception e) {
            return "OrtValue{type=" + onnxType() + "}";
        }
    }
}

