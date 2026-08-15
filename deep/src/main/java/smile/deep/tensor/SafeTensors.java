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
package smile.deep.tensor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Safetensors is a simple format for storing tensors safely (as opposed to
 * pickle) with zero-copy reads. This class implements reading and writing
 * the safetensors binary format.
 *
 * @param tensors the name-to-tensor map.
 * @param metadata the free-form string-to-string metadata map.
 * @see <a href="https://github.com/safetensors/safetensors">Safetensors specification</a>
 * @author Haifeng Li
 */
public record SafeTensors(Map<String, Tensor> tensors, Map<String, String> metadata) {
    /**
     * Reads a safetensors file from disk, materialising every tensor.
     *
     * @param path the file path to read.
     * @param device the device on which to store the loaded tensors.
     * @return the parsed safetensors file.
     * @throws IOException if an I/O error occurs.
     */
    public static SafeTensors read(String path, Device device) throws IOException {
        return read(path, device, null);
    }

    /**
     * Reads selected tensors from a safetensors file.
     *
     * <p>Each tensor is loaded with a positioned {@link FileChannel#read} of its
     * {@code data_offsets} range. The data region is <em>not</em> memory-mapped as
     * a whole, so shards larger than {@link Integer#MAX_VALUE} bytes (common for
     * multi-gigabyte LLM checkpoints) load correctly. Individual tensors must still
     * fit in a Java {@link ByteBuffer} ({@code ≤ Integer.MAX_VALUE} bytes).
     *
     * @param path the file path to read.
     * @param device the device on which to store the loaded tensors.
     * @param include when non-{@code null}, only tensors whose names are in this
     *                collection are materialised; {@code null} loads all tensors.
     * @return the parsed safetensors subset (metadata is always populated).
     * @throws IOException if an I/O error occurs.
     */
    public static SafeTensors read(String path, Device device, Collection<String> include)
            throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "r");
             FileChannel channel = file.getChannel()) {

            Header header = readHeader(channel);
            Map<String, Tensor> tensors = new HashMap<>();
            for (var entry : header.tensors.entrySet()) {
                String name = entry.getKey();
                if (include != null && !include.contains(name)) {
                    continue;
                }
                TensorInfo info = entry.getValue();
                ByteBuffer bytes = readRange(channel, header.dataStartOffset + info.begin, info.end - info.begin);
                tensors.put(name, toTensor(info.dtype, bytes, bytes.remaining(), info.shape, device));
            }
            return new SafeTensors(tensors, header.metadata);
        }
    }

    /**
     * Returns the tensor names declared in a safetensors header without loading
     * any tensor data. Suitable for building a weight map from a single large
     * checkpoint file.
     *
     * @param path the safetensors file path.
     * @return tensor names in header order (excluding {@code __metadata__}).
     * @throws IOException if an I/O error occurs.
     */
    public static List<String> listTensors(String path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "r");
             FileChannel channel = file.getChannel()) {
            return new ArrayList<>(readHeader(channel).tensors.keySet());
        }
    }

    /**
     * Parses the safetensors JSON header and returns tensor descriptors without
     * reading the data region.
     */
    private static Header readHeader(FileChannel channel) throws IOException {
        // 1. Read the 8-byte header length (little-endian).
        ByteBuffer lengthBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, lengthBuffer, 0);
        lengthBuffer.flip();
        long headerLength = lengthBuffer.getLong();
        if (headerLength < 0 || headerLength > Integer.MAX_VALUE) {
            throw new IOException("Invalid safetensors header length: " + headerLength);
        }

        // 2. Read the JSON header.
        ByteBuffer headerBuffer = ByteBuffer.allocate((int) headerLength).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, headerBuffer, 8);
        headerBuffer.flip();
        String jsonHeader = StandardCharsets.UTF_8.decode(headerBuffer).toString();

        long dataStartOffset = 8 + headerLength;
        JsonNode root = new ObjectMapper().readTree(jsonHeader);
        Map<String, TensorInfo> tensors = new LinkedHashMap<>();
        Map<String, String> metadata = new HashMap<>();
        for (var entry : root.properties()) {
            String name = entry.getKey();
            JsonNode node = entry.getValue();

            if (name.equals("__metadata__")) {
                for (var meta : node.properties()) {
                    metadata.put(meta.getKey(), meta.getValue().asString());
                }
                continue;
            }

            String dtype = node.get("dtype").asString();
            JsonNode shapeNode = node.get("shape");
            long[] shape = new long[shapeNode.size()];
            for (int i = 0; i < shape.length; i++) {
                shape[i] = shapeNode.get(i).asLong();
            }

            JsonNode offsets = node.get("data_offsets");
            long begin = offsets.get(0).asLong();
            long end = offsets.get(1).asLong();
            if (begin < 0 || end < begin) {
                throw new IOException("Invalid data_offsets for tensor '" + name + "': [" + begin + ", " + end + "]");
            }
            tensors.put(name, new TensorInfo(dtype, shape, begin, end));
        }
        return new Header(dataStartOffset, tensors, metadata);
    }

    /**
     * Reads {@code length} bytes starting at absolute file {@code position}
     * into a little-endian heap buffer.
     */
    private static ByteBuffer readRange(FileChannel channel, long position, long length)
            throws IOException {
        if (length < 0) {
            throw new IOException("Negative safetensors data length: " + length);
        }
        if (length > Integer.MAX_VALUE) {
            throw new IOException(
                    "Tensor data block exceeds Integer.MAX_VALUE bytes (" + length + ")");
        }
        ByteBuffer buf = ByteBuffer.allocate((int) length).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, buf, position);
        buf.flip();
        return buf;
    }

    /** Fills {@code buf} by reading from {@code channel} at {@code position}. */
    private static void readFully(FileChannel channel, ByteBuffer buf, long position)
            throws IOException {
        long pos = position;
        while (buf.hasRemaining()) {
            int n = channel.read(buf, pos);
            if (n < 0) {
                throw new IOException("Unexpected EOF reading safetensors at offset " + pos);
            }
            pos += n;
        }
    }

    /** Header + per-tensor descriptors (offsets relative to the data region). */
    private record Header(long dataStartOffset,
                          Map<String, TensorInfo> tensors,
                          Map<String, String> metadata) {}

    /** Descriptor for one tensor in the safetensors header. */
    private record TensorInfo(String dtype, long[] shape, long begin, long end) {}

    /**
     * Writes this {@code SafeTensors} object to a file.
     *
     * <p>The file is created (or truncated) at {@code path}. The layout is identical
     * to the format described in {@link #read(String, Device)}: an 8-byte little-endian
     * header-length prefix, a UTF-8 JSON header, and then the raw little-endian tensor
     * bytes. {@code __metadata__} is written first in the JSON object when the metadata
     * map is non-empty.
     *
     * <p>Unsigned integer types are serialised by reinterpreting the bit pattern through
     * the corresponding signed type, which preserves the raw bytes without any numeric
     * conversion. {@link ScalarType#Half} and {@link ScalarType#BFloat16} tensors are
     * converted element-wise to {@code float32} and their bits are re-encoded. F8 types
     * follow the same float32 intermediate and then re-encode via the respective
     * E4M3FN / E5M2 format rules.
     *
     * @param path the file path to write.
     * @throws IOException if an I/O error occurs.
     * @throws UnsupportedOperationException if a tensor has a dtype that cannot be serialised.
     */
    public void write(String path) throws IOException {
        // Compute raw bytes for every tensor up front so offsets are known before writing.
        List<String> names = new ArrayList<>(tensors.size());
        List<byte[]> chunks = new ArrayList<>(tensors.size());
        for (var entry : tensors.entrySet()) {
            names.add(entry.getKey());
            chunks.add(tensorBytes(entry.getValue()));
        }

        // Build the JSON header.
        ObjectMapper om = new ObjectMapper();
        ObjectNode root = om.createObjectNode();
        if (!metadata.isEmpty()) {
            ObjectNode meta = root.putObject("__metadata__");
            metadata.forEach(meta::put);
        }
        long offset = 0;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Tensor t = tensors.get(name);
            byte[] data = chunks.get(i);
            ObjectNode node = root.putObject(name);
            node.put("dtype", safetensorsDtype(t.dtype()));
            ArrayNode shapeArr = node.putArray("shape");
            for (long dim : t.shape()) shapeArr.add(dim);
            node.putArray("data_offsets").add(offset).add(offset + data.length);
            offset += data.length;
        }
        byte[] header = root.toString().getBytes(StandardCharsets.UTF_8);

        // Write: 8-byte LE length + JSON header + tensor data.
        try (FileChannel channel = FileChannel.open(Path.of(path),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer lenBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            lenBuf.putLong(header.length);
            lenBuf.flip();
            channel.write(lenBuf);
            channel.write(ByteBuffer.wrap(header));
            for (byte[] chunk : chunks) {
                channel.write(ByteBuffer.wrap(chunk));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Read helpers
    // -------------------------------------------------------------------------

    /**
     * Materialises a tensor from its raw little-endian byte buffer.
     * @param dtype the safetensors dtype string.
     * @param buffer the little-endian tensor bytes.
     * @param length the number of bytes.
     * @param shape the tensor shape.
     * @param device the device to store the tensor.
     * @return the tensor stored on the given device.
     */
    private static Tensor toTensor(String dtype, ByteBuffer buffer, int length, long[] shape, Device device) {
        switch (dtype) {
            case "BOOL" -> {
                boolean[] data = new boolean[length];
                for (int i = 0; i < length; i++) data[i] = buffer.get(i) != 0;
                return Tensor.of(data, shape).to(device);
            }
            case "I8" -> {
                byte[] data = new byte[length];
                buffer.get(data);
                return Tensor.of(data, shape).to(device);
            }
            case "U8" -> {
                // Signed Java byte carrier; bit pattern preserved by PyTorch's int8→uint8 cast.
                byte[] data = new byte[length];
                buffer.get(data);
                return Tensor.of(data, shape).to(device, ScalarType.UInt8);
            }
            case "I16" -> {
                short[] data = new short[length / 2];
                buffer.asShortBuffer().get(data);
                return Tensor.of(data, shape).to(device);
            }
            case "U16" -> {
                short[] data = new short[length / 2];
                buffer.asShortBuffer().get(data);
                return Tensor.of(data, shape).to(device, ScalarType.UInt16);
            }
            case "I32" -> {
                int[] data = new int[length / 4];
                buffer.asIntBuffer().get(data);
                return Tensor.of(data, shape).to(device);
            }
            case "U32" -> {
                int[] data = new int[length / 4];
                buffer.asIntBuffer().get(data);
                return Tensor.of(data, shape).to(device, ScalarType.UInt32);
            }
            case "I64" -> {
                long[] data = new long[length / 8];
                buffer.asLongBuffer().get(data);
                return Tensor.of(data, shape).to(device);
            }
            case "U64" -> {
                long[] data = new long[length / 8];
                buffer.asLongBuffer().get(data);
                return Tensor.of(data, shape).to(device, ScalarType.UInt64);
            }
            case "F32" -> {
                float[] data = new float[length / 4];
                buffer.asFloatBuffer().get(data);
                return Tensor.of(data, shape).to(device);
            }
            case "F64" -> {
                double[] data = new double[length / 8];
                buffer.asDoubleBuffer().get(data);
                return Tensor.of(data, shape).to(device);
            }
            case "F16" -> {
                // No Java primitive for half; widen to float (lossless) and let torch re-narrow.
                short[] bits = new short[length / 2];
                buffer.asShortBuffer().get(bits);
                float[] data = new float[bits.length];
                for (int i = 0; i < bits.length; i++) data[i] = Float.float16ToFloat(bits[i]);
                return Tensor.of(data, shape).to(device, ScalarType.Half);
            }
            case "BF16" -> {
                // bfloat16 is the upper 16 bits of a float32; restore by left-shifting.
                short[] bits = new short[length / 2];
                buffer.asShortBuffer().get(bits);
                float[] data = new float[bits.length];
                for (int i = 0; i < bits.length; i++) data[i] = Float.intBitsToFloat((bits[i] & 0xFFFF) << 16);
                return Tensor.of(data, shape).to(device, ScalarType.BFloat16);
            }
            case "F8_E4M3" -> {
                float[] data = new float[length];
                for (int i = 0; i < length; i++) data[i] = float8e4m3ToFloat(buffer.get(i));
                return Tensor.of(data, shape).to(device, ScalarType.Float8e4m3fn);
            }
            case "F8_E5M2" -> {
                float[] data = new float[length];
                for (int i = 0; i < length; i++) data[i] = float8e5m2ToFloat(buffer.get(i));
                return Tensor.of(data, shape).to(device, ScalarType.Float8e5m2);
            }
            default -> throw new UnsupportedOperationException("Unsupported safetensors dtype: " + dtype);
        }
    }

    /** Decodes a Float8_E4M3FN byte (1-4-3, bias 7, no infinities, NaN=0x7F) to float. */
    private static float float8e4m3ToFloat(byte b) {
        int bits = b & 0xFF;
        int sign = (bits >>> 7) & 0x1;
        int exp = (bits >>> 3) & 0xF;
        int man = bits & 0x7;
        float value;
        if (exp == 0xF && man == 0x7) {
            return Float.NaN;
        } else if (exp == 0) {
            value = (float) Math.scalb(man / 8.0, 1 - 7);
        } else {
            value = (float) Math.scalb(1.0 + man / 8.0, exp - 7);
        }
        return sign == 1 ? -value : value;
    }

    /** Decodes a Float8_E5M2 byte (1-5-2, bias 15, IEEE-like with infinities and NaN) to float. */
    private static float float8e5m2ToFloat(byte b) {
        int bits = b & 0xFF;
        int sign = (bits >>> 7) & 0x1;
        int exp = (bits >>> 2) & 0x1F;
        int man = bits & 0x3;
        float value;
        if (exp == 0x1F) {
            if (man != 0) return Float.NaN;
            return sign == 1 ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        } else if (exp == 0) {
            value = (float) Math.scalb(man / 4.0, 1 - 15);
        } else {
            value = (float) Math.scalb(1.0 + man / 4.0, exp - 15);
        }
        return sign == 1 ? -value : value;
    }

    // -------------------------------------------------------------------------
    // Write helpers
    // -------------------------------------------------------------------------

    /** Returns the safetensors dtype string for a given {@link ScalarType}. */
    private static String safetensorsDtype(ScalarType dtype) {
        return switch (dtype) {
            case Bool     -> "BOOL";
            case Int8     -> "I8";
            case UInt8    -> "U8";
            case Int16    -> "I16";
            case UInt16   -> "U16";
            case Int32    -> "I32";
            case UInt32   -> "U32";
            case Int64    -> "I64";
            case UInt64   -> "U64";
            case Half     -> "F16";
            case BFloat16 -> "BF16";
            case Float    -> "F32";
            case Double   -> "F64";
            case Float8e4m3fn -> "F8_E4M3";
            case Float8e5m2   -> "F8_E5M2";
            default -> throw new UnsupportedOperationException(
                    "Unsupported tensor dtype for serialisation: " + dtype);
        };
    }

    /**
     * Serialises a tensor to its raw little-endian byte representation.
     * Unsigned integer types are reinterpreted through the corresponding signed
     * type so the bit pattern is preserved unchanged. F16, BF16, and F8 types
     * are converted via float32 as an intermediate.
     */
    private static byte[] tensorBytes(Tensor tensor) {
        Tensor c = tensor.contiguous();
        try {
            return switch (c.dtype()) {
                case Bool, Int8 -> c.byteArray();
                case UInt8 -> {
                    Tensor t = c.to(ScalarType.Int8);
                    try { yield t.byteArray(); } finally { t.close(); }
                }
                case Int16 -> toLeBytes(c.shortArray());
                case UInt16 -> {
                    Tensor t = c.to(ScalarType.Int16);
                    try { yield toLeBytes(t.shortArray()); } finally { t.close(); }
                }
                case Int32 -> toLeBytes(c.intArray());
                case UInt32 -> {
                    Tensor t = c.to(ScalarType.Int32);
                    try { yield toLeBytes(t.intArray()); } finally { t.close(); }
                }
                case Int64 -> toLeBytes(c.longArray());
                case UInt64 -> {
                    Tensor t = c.to(ScalarType.Int64);
                    try { yield toLeBytes(t.longArray()); } finally { t.close(); }
                }
                case Float -> toLeBytes(c.floatArray());
                case Double -> toLeBytes(c.doubleArray());
                case Half -> {
                    Tensor t = c.to(ScalarType.Float);
                    try {
                        float[] floats = t.floatArray();
                        short[] shorts = new short[floats.length];
                        for (int i = 0; i < floats.length; i++) shorts[i] = Float.floatToFloat16(floats[i]);
                        yield toLeBytes(shorts);
                    } finally { t.close(); }
                }
                case BFloat16 -> {
                    Tensor t = c.to(ScalarType.Float);
                    try {
                        float[] floats = t.floatArray();
                        short[] shorts = new short[floats.length];
                        for (int i = 0; i < floats.length; i++) {
                            shorts[i] = (short) (Float.floatToRawIntBits(floats[i]) >>> 16);
                        }
                        yield toLeBytes(shorts);
                    } finally { t.close(); }
                }
                case Float8e4m3fn -> {
                    Tensor t = c.to(ScalarType.Float);
                    try {
                        float[] floats = t.floatArray();
                        byte[] bytes = new byte[floats.length];
                        for (int i = 0; i < floats.length; i++) bytes[i] = floatToFloat8e4m3(floats[i]);
                        yield bytes;
                    } finally { t.close(); }
                }
                case Float8e5m2 -> {
                    Tensor t = c.to(ScalarType.Float);
                    try {
                        float[] floats = t.floatArray();
                        byte[] bytes = new byte[floats.length];
                        for (int i = 0; i < floats.length; i++) bytes[i] = floatToFloat8e5m2(floats[i]);
                        yield bytes;
                    } finally { t.close(); }
                }
                default -> throw new UnsupportedOperationException(
                        "Unsupported tensor dtype for serialisation: " + c.dtype());
            };
        } finally {
            c.close();
        }
    }

    private static byte[] toLeBytes(short[] a) {
        ByteBuffer b = ByteBuffer.allocate(a.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.asShortBuffer().put(a);
        return b.array();
    }

    private static byte[] toLeBytes(int[] a) {
        ByteBuffer b = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.asIntBuffer().put(a);
        return b.array();
    }

    private static byte[] toLeBytes(long[] a) {
        ByteBuffer b = ByteBuffer.allocate(a.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        b.asLongBuffer().put(a);
        return b.array();
    }

    private static byte[] toLeBytes(float[] a) {
        ByteBuffer b = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.asFloatBuffer().put(a);
        return b.array();
    }

    private static byte[] toLeBytes(double[] a) {
        ByteBuffer b = ByteBuffer.allocate(a.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        b.asDoubleBuffer().put(a);
        return b.array();
    }

    /**
     * Encodes a {@code float} value to a Float8_E4M3FN byte
     * (1 sign, 4 exponent bits with bias 7, 3 mantissa bits; no infinities;
     * {@code 0x7F} = NaN; max finite value = 448.0).
     */
    private static byte floatToFloat8e4m3(float f) {
        if (Float.isNaN(f)) return 0x7F;
        int sign = Float.floatToRawIntBits(f) >>> 31;
        float abs = Math.abs(f);
        if (Float.isInfinite(f) || abs > 448.0f) abs = 448.0f;
        if (abs == 0.0f) return (byte) (sign << 7);

        int exp = Math.getExponent(abs);   // unbiased IEEE exponent = floor(log2(abs))
        int biasedExp = exp + 7;
        int man;
        if (biasedExp <= 0) {
            // Denormal: value = man/8 * 2^(1-7)  →  man = round(abs * 2^9 / 8) = round(abs * 64)
            biasedExp = 0;
            man = Math.round(abs * 64.0f);  // = abs / scalb(1, 1-7) * 8 = abs * 2^6 * 8... wait
            // Actually: denormal value = man/8 * 2^(1-7) = man * 2^(-9)
            // so man = round(abs / 2^(-9)) = round(abs * 512)
            man = Math.min(Math.round(abs * 512.0f), 7);
        } else {
            float mantissaFrac = abs / Math.scalb(1.0f, exp) - 1.0f;
            man = Math.round(mantissaFrac * 8.0f);
            if (man >= 8) { biasedExp++; man = 0; }
            biasedExp = Math.min(biasedExp, 15);
            man = Math.min(man, 7);
            // exp=15, man=7 is the NaN encoding; clamp to max-finite instead.
            if (biasedExp == 15 && man == 7) man = 6;
        }
        return (byte) ((sign << 7) | (biasedExp << 3) | man);
    }

    /**
     * Encodes a {@code float} value to a Float8_E5M2 byte
     * (1 sign, 5 exponent bits with bias 15, 2 mantissa bits; IEEE-like with
     * infinities and NaN; max finite value = 57344.0).
     */
    private static byte floatToFloat8e5m2(float f) {
        if (Float.isNaN(f)) return 0x7F;                           // 0 11111 11
        if (f == Float.POSITIVE_INFINITY) return 0x7C;             // 0 11111 00
        if (f == Float.NEGATIVE_INFINITY) return (byte) 0xFC;      // 1 11111 00
        int sign = Float.floatToRawIntBits(f) >>> 31;
        float abs = Math.abs(f);
        float maxVal = (float) Math.scalb(1.0 + 3.0 / 4.0, 15);   // 57344.0
        if (abs > maxVal) abs = maxVal;
        if (abs == 0.0f) return (byte) (sign << 7);

        int exp = Math.getExponent(abs);
        int biasedExp = exp + 15;
        int man;
        if (biasedExp <= 0) {
            biasedExp = 0;
            man = Math.min(Math.round(abs * (float) Math.scalb(1.0, 16)), 3);
        } else {
            float mantissaFrac = abs / Math.scalb(1.0f, exp) - 1.0f;
            man = Math.round(mantissaFrac * 4.0f);
            if (man >= 4) { biasedExp++; man = 0; }
            biasedExp = Math.min(biasedExp, 30);  // 31 is reserved for Inf/NaN
            man = Math.min(man, 3);
        }
        return (byte) ((sign << 7) | (biasedExp << 2) | man);
    }
}
