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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal hand-rolled parser for ONNX {@code TensorProto} serialized in
 * Protocol Buffers binary format (proto3). Only the fields required to read
 * the light-model expected-output {@code .pb} test fixtures are decoded:
 *
 * <pre>
 * message TensorProto {
 *   repeated int64 dims      = 1;   // wire-type 0 (varint)
 *   int32          data_type = 2;   // wire-type 0 (varint)
 *   repeated float float_data  = 5; // wire-type 2 (length-delimited, packed)
 *   repeated int32 int32_data  = 6; // wire-type 2 (length-delimited, packed)
 *   repeated int64 int64_data  = 8; // wire-type 2 (length-delimited, packed)
 *   string         name        = 9; // wire-type 2 (length-delimited)
 *   bytes          raw_data    = 10;// wire-type 2 (length-delimited)
 *   repeated double double_data = 11;// wire-type 2 (length-delimited, packed)
 * }
 * </pre>
 *
 * <p>All scalar fields use little-endian byte order per the ONNX spec.
 *
 * @author Haifeng Li
 */
public class TensorProtoReader {

    /**
     * Data type constants matching ONNX {@code TensorProto.DataType}.
     * These correspond to {@link ElementType} ordinal values.
     */
    public static final int DATA_TYPE_FLOAT   = 1;
    public static final int DATA_TYPE_UINT8   = 2;
    public static final int DATA_TYPE_INT8    = 3;
    public static final int DATA_TYPE_UINT16  = 4;
    public static final int DATA_TYPE_INT16   = 5;
    public static final int DATA_TYPE_INT32   = 6;
    public static final int DATA_TYPE_INT64   = 7;
    public static final int DATA_TYPE_STRING  = 8;
    public static final int DATA_TYPE_BOOL    = 9;
    public static final int DATA_TYPE_DOUBLE  = 11;

    /** Shape of the tensor. */
    private final long[] dims;
    /** ONNX data type code. */
    private final int dataType;
    /** Optional tensor name. */
    private final String name;
    /** Raw float data (only set when data_type == FLOAT and float_data or raw_data is present). */
    private final float[] floatData;
    /** Raw double data. */
    private final double[] doubleData;
    /** Raw int32 data. */
    private final int[] int32Data;
    /** Raw int64 data. */
    private final long[] int64Data;

    private TensorProtoReader(long[] dims, int dataType, String name,
                               float[] floatData, double[] doubleData,
                               int[] int32Data, long[] int64Data) {
        this.dims       = dims;
        this.dataType   = dataType;
        this.name       = name;
        this.floatData  = floatData;
        this.doubleData = doubleData;
        this.int32Data  = int32Data;
        this.int64Data  = int64Data;
    }

    // -----------------------------------------------------------------------
    // Public accessors
    // -----------------------------------------------------------------------

    /** Returns the tensor shape. */
    public long[] dims() { return dims; }

    /** Returns the ONNX data type code. */
    public int dataType() { return dataType; }

    /** Returns the tensor name (may be empty). */
    public String name() { return name; }

    /**
     * Returns the float values of this tensor.
     * Valid when {@link #dataType()} == {@link #DATA_TYPE_FLOAT}.
     */
    public float[] floatData() { return floatData; }

    /**
     * Returns the double values of this tensor.
     * Valid when {@link #dataType()} == {@link #DATA_TYPE_DOUBLE}.
     */
    public double[] doubleData() { return doubleData; }

    /**
     * Returns the int32 values of this tensor.
     * Valid when {@link #dataType()} == {@link #DATA_TYPE_INT32}.
     */
    public int[] int32Data() { return int32Data; }

    /**
     * Returns the int64 values of this tensor.
     * Valid when {@link #dataType()} == {@link #DATA_TYPE_INT64}.
     */
    public long[] int64Data() { return int64Data; }

    /**
     * Returns the total number of elements ({@code product of dims}).
     */
    public long elementCount() {
        long n = 1;
        for (long d : dims) n *= d;
        return n;
    }

    // -----------------------------------------------------------------------
    // Factory / parsing
    // -----------------------------------------------------------------------

    /**
     * Reads a {@code TensorProto} from the given path.
     *
     * @param path path to the {@code .pb} file.
     * @return the parsed {@code TensorProtoReader}.
     * @throws IOException if reading fails.
     */
    public static TensorProtoReader read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return parse(bytes);
    }

    /**
     * Reads a {@code TensorProto} from the given input stream.
     *
     * @param in the input stream.
     * @return the parsed {@code TensorProtoReader}.
     * @throws IOException if reading fails.
     */
    public static TensorProtoReader read(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        return parse(bytes);
    }

    // -----------------------------------------------------------------------
    // Protobuf binary decoder
    // -----------------------------------------------------------------------

    private static TensorProtoReader parse(byte[] buf) {
        int pos = 0;
        final int len = buf.length;

        List<Long>  dims       = new ArrayList<>();
        int         dataType   = 0;
        String      name       = "";
        float[]     floatData  = null;
        double[]    doubleData = null;
        int[]       int32Data  = null;
        long[]      int64Data  = null;

        while (pos < len) {
            // Read tag (field_number << 3 | wire_type)
            long[] tagResult = readVarint(buf, pos);
            int tag  = (int) tagResult[0];
            pos      = (int) tagResult[1];

            int fieldNum  = tag >>> 3;
            int wireType  = tag & 0x07;

            switch (wireType) {
                case 0 -> { // varint
                    long[] vr  = readVarint(buf, pos);
                    long value = vr[0];
                    pos        = (int) vr[1];

                    if (fieldNum == 1) dims.add(value);             // dims
                    else if (fieldNum == 2) dataType = (int) value; // data_type
                }
                case 1 -> pos += 8; // 64-bit fixed — skip
                case 2 -> { // length-delimited
                    long[] lr  = readVarint(buf, pos);
                    int bLen   = (int) lr[0];
                    pos        = (int) lr[1];
                    int start  = pos;
                    pos       += bLen;

                    switch (fieldNum) {
                        case 5  -> floatData  = unpackFloats(buf, start, bLen);
                        case 6  -> int32Data  = unpackInt32s(buf, start, bLen);
                        case 8  -> int64Data  = unpackInt64s(buf, start, bLen);
                        default -> {
                            // raw_data: little-endian flat bytes; decode for all numeric types.
                            // The caller selects the right one after the loop based on dataType.
                            // NOTE: field 2 (data_type) always appears before field 10 in these
                            // files, but we decode all four to be safe.
                            byte[] raw = java.util.Arrays.copyOfRange(buf, start, start + bLen);
                            floatData  = decodeRawAsFloats(raw);
                            doubleData = decodeRawAsDoubles(raw);
                            int32Data  = decodeRawAsInt32s(raw);
                            int64Data  = decodeRawAsInt64s(raw);
                        }
                    }
                }
                case 5 -> pos += 4; // 32-bit fixed — skip
                default -> throw new IllegalStateException(
                        "Unknown protobuf wire type " + wireType + " at offset " + (pos - 1));
            }
        }

        long[] dimsArr = dims.stream().mapToLong(Long::longValue).toArray();

        // After parsing, keep only the data array for the actual data type.
        // raw_data was decoded into all four arrays above; select the right one.
        float[]  finalFloat  = null;
        double[] finalDouble = null;
        int[]    finalInt32  = null;
        long[]   finalInt64  = null;

        switch (dataType) {
            case DATA_TYPE_FLOAT  -> finalFloat  = floatData;
            case DATA_TYPE_DOUBLE -> finalDouble = doubleData;
            case DATA_TYPE_INT32  -> finalInt32  = int32Data;
            case DATA_TYPE_INT64  -> finalInt64  = int64Data;
            default -> {
                // Use whatever was set (covers float_data / int32_data / etc.)
                finalFloat  = floatData;
                finalDouble = doubleData;
                finalInt32  = int32Data;
                finalInt64  = int64Data;
            }
        }

        return new TensorProtoReader(dimsArr, dataType, name,
                finalFloat, finalDouble, finalInt32, finalInt64);
    }

    // -----------------------------------------------------------------------
    // Protobuf varint decoder
    // -----------------------------------------------------------------------

    /**
     * Reads a base-128 varint from {@code buf} starting at {@code pos}.
     * Returns a two-element array: {@code [value, newPos]}.
     */
    private static long[] readVarint(byte[] buf, int pos) {
        long result = 0;
        int  shift  = 0;
        while (true) {
            byte b = buf[pos++];
            result |= (long)(b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return new long[]{ result, pos };
    }

    // -----------------------------------------------------------------------
    // Packed-repeated field decoders
    // -----------------------------------------------------------------------

    private static float[] unpackFloats(byte[] buf, int start, int byteLen) {
        int count = byteLen / 4;
        float[] arr = new float[count];
        ByteBuffer bb = ByteBuffer.wrap(buf, start, byteLen).order(ByteOrder.LITTLE_ENDIAN);
        bb.asFloatBuffer().get(arr);
        return arr;
    }

    private static double[] unpackDoubles(byte[] buf, int start, int byteLen) {
        int count = byteLen / 8;
        double[] arr = new double[count];
        ByteBuffer bb = ByteBuffer.wrap(buf, start, byteLen).order(ByteOrder.LITTLE_ENDIAN);
        bb.asDoubleBuffer().get(arr);
        return arr;
    }

    private static int[] unpackInt32s(byte[] buf, int start, int byteLen) {
        int count = byteLen / 4;
        int[] arr = new int[count];
        ByteBuffer bb = ByteBuffer.wrap(buf, start, byteLen).order(ByteOrder.LITTLE_ENDIAN);
        bb.asIntBuffer().get(arr);
        return arr;
    }

    private static long[] unpackInt64s(byte[] buf, int start, int byteLen) {
        int count = byteLen / 8;
        long[] arr = new long[count];
        ByteBuffer bb = ByteBuffer.wrap(buf, start, byteLen).order(ByteOrder.LITTLE_ENDIAN);
        bb.asLongBuffer().get(arr);
        return arr;
    }

    // -----------------------------------------------------------------------
    // raw_data decoders (we don't know type at the time of parsing, so we
    // decode to all four types and select after the loop)
    // -----------------------------------------------------------------------

    private static float[] decodeRawAsFloats(byte[] raw) {
        if (raw.length % 4 != 0) return null;
        float[] arr = new float[raw.length / 4];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(arr);
        return arr;
    }

    private static double[] decodeRawAsDoubles(byte[] raw) {
        if (raw.length % 8 != 0) return null;
        double[] arr = new double[raw.length / 8];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(arr);
        return arr;
    }

    private static int[] decodeRawAsInt32s(byte[] raw) {
        if (raw.length % 4 != 0) return null;
        int[] arr = new int[raw.length / 4];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(arr);
        return arr;
    }

    private static long[] decodeRawAsInt64s(byte[] raw) {
        if (raw.length % 8 != 0) return null;
        long[] arr = new long[raw.length / 8];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(arr);
        return arr;
    }

    @Override
    public String toString() {
        return "TensorProto{name='" + name + "', dataType=" + dataType +
                ", dims=" + java.util.Arrays.toString(dims) +
                ", elementCount=" + elementCount() + "}";
    }
}

