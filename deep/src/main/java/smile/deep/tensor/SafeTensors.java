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
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Safetensors is a new simple format for storing tensors safely
 * (as opposed to pickle) and that is still fast (zero-copy).
 *
 * @param tensors the name-to-tensor map.
 * @param metadata the free form string-to-string map.
 * @author Haifeng Li
 */
public record SafeTensors(Map<String, Tensor> tensors, Map<String, String> metadata) {
    /**
     *
     * @param path the file name.
     * @param device the device to store loaded tensors.
     * @throws IOException if failed to read the file.
     */
    static SafeTensors read(String path, Device device) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "r");
             FileChannel channel = file.getChannel()) {

            // 1. Read the 8-byte header length (Little Endian)
            ByteBuffer lengthBuffer = ByteBuffer.allocate(8);
            lengthBuffer.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(lengthBuffer);
            lengthBuffer.flip();
            long headerLength = lengthBuffer.getLong();

            // 2. Read the JSON Header
            ByteBuffer headerBuffer = ByteBuffer.allocate((int) headerLength);
            channel.read(headerBuffer);
            headerBuffer.flip();
            String jsonHeader = StandardCharsets.UTF_8.decode(headerBuffer).toString();

            // 3. Memory-map the remaining byte buffer for tensor data.
            long dataStartOffset = 8 + headerLength;
            long dataLength = channel.size() - dataStartOffset;
            java.nio.MappedByteBuffer dataBuffer = channel.map(
                    FileChannel.MapMode.READ_ONLY, dataStartOffset, dataLength
            );
            dataBuffer.order(ByteOrder.LITTLE_ENDIAN);

            // 4. Parse the JSON header and materialize each tensor.
            JsonNode root = new ObjectMapper().readTree(jsonHeader);
            Map<String, Tensor> tensors = new HashMap<>();
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
                int length = (int) (end - begin);
                ByteBuffer bytes = dataBuffer.slice((int) begin, length).order(ByteOrder.LITTLE_ENDIAN);

                tensors.put(name, toTensor(dtype, bytes, length, shape, device));
            }

            return new SafeTensors(tensors, metadata);
        }
    }

    /**
     * Materializes a tensor from its raw little-endian byte buffer.
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
                // Signed carrier reinterpreted as unsigned; the bit pattern is preserved.
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
                // bfloat16 is the upper 16 bits of a float32; widen by left-padding with zeros.
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

    /** Decodes a Float8_E4M3FN byte (1-4-3, bias 7, no infinities) to float. */
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

    /** Decodes a Float8_E5M2 byte (1-5-2, bias 15, IEEE-like) to float. */
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
}
