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

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link SafeTensors}: write a file then read it back and
 * verify that each tensor's dtype, shape, and element values are preserved.
 *
 * @author Haifeng Li
 */
public class SafeTensorsTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // Scalar floating-point types
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFloat32TensorWhenRoundTrippedThenValuesPreserved() throws Exception {
        float[] expected = {1.0f, -2.5f, 3.14f, 0.0f};
        Tensor t = Tensor.of(expected, 4);
        String path = tempDir.resolve("f32.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.Float, loaded.dtype());
        assertArrayEquals(new long[]{4}, loaded.shape());
        assertArrayEquals(expected, loaded.floatArray(), 1e-6f);

        t.close();
        loaded.close();
    }

    @Test
    public void testGivenFloat64TensorWhenRoundTrippedThenValuesPreserved() throws Exception {
        double[] expected = {1.0, -2.5, 3.14159, 0.0};
        Tensor t = Tensor.of(expected, 4);
        String path = tempDir.resolve("f64.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.Double, loaded.dtype());
        assertArrayEquals(expected, loaded.doubleArray(), 1e-12);

        t.close();
        loaded.close();
    }

    @Test
    public void testGivenHalfPrecisionTensorWhenRoundTrippedThenValuesPreserved() throws Exception {
        // Use values that are exactly representable in float16.
        float[] expected = {1.0f, -2.0f, 4.0f, 0.5f};
        Tensor t = Tensor.of(expected, 4).to(ScalarType.Half);
        String path = tempDir.resolve("f16.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.Half, loaded.dtype());
        assertArrayEquals(new long[]{4}, loaded.shape());
        // Convert to float32 to inspect values.
        Tensor asFloat = loaded.to(ScalarType.Float);
        assertArrayEquals(expected, asFloat.floatArray(), 1e-3f);

        t.close();
        loaded.close();
        asFloat.close();
    }

    @Test
    public void testGivenBFloat16TensorWhenRoundTrippedThenValuesPreserved() throws Exception {
        // Use values that are exactly representable in bfloat16.
        float[] expected = {1.0f, -2.0f, 4.0f, -0.5f};
        Tensor t = Tensor.of(expected, 4).to(ScalarType.BFloat16);
        String path = tempDir.resolve("bf16.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.BFloat16, loaded.dtype());
        Tensor asFloat = loaded.to(ScalarType.Float);
        assertArrayEquals(expected, asFloat.floatArray(), 1e-2f);

        t.close();
        loaded.close();
        asFloat.close();
    }

    // -----------------------------------------------------------------------
    // Signed integer types
    // -----------------------------------------------------------------------

    @Test
    public void testGivenInt8TensorWhenRoundTrippedThenValuesPreserved() throws Exception {
        byte[] expected = {1, -1, 127, -128};
        Tensor t = Tensor.of(expected, 4);
        String path = tempDir.resolve("i8.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.Int8, loaded.dtype());
        assertArrayEquals(expected, loaded.byteArray());

        t.close();
        loaded.close();
    }

    @Test
    public void testGivenInt16TensorWhenRoundTrippedThenValuesPreserved() throws Exception {
        short[] expected = {1, -1, 32767, -32768};
        Tensor t = Tensor.of(expected, 4);
        String path = tempDir.resolve("i16.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.Int16, loaded.dtype());
        assertArrayEquals(expected, loaded.shortArray());

        t.close();
        loaded.close();
    }

    @Test
    public void testGivenInt32TensorWhenRoundTrippedThenValuesPreserved() throws Exception {
        int[] expected = {1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE};
        Tensor t = Tensor.of(expected, 4);
        String path = tempDir.resolve("i32.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.Int32, loaded.dtype());
        assertArrayEquals(expected, loaded.intArray());

        t.close();
        loaded.close();
    }

    @Test
    public void testGivenInt64TensorWhenRoundTrippedThenValuesPreserved() throws Exception {
        long[] expected = {1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE};
        Tensor t = Tensor.of(expected, 4);
        String path = tempDir.resolve("i64.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.Int64, loaded.dtype());
        assertArrayEquals(expected, loaded.longArray());

        t.close();
        loaded.close();
    }

    // -----------------------------------------------------------------------
    // Unsigned integer types — verify high-bit values survive the signed carrier
    // -----------------------------------------------------------------------

    @Test
    public void testGivenUInt8TensorWhenRoundTrippedThenBitPatternPreserved() throws Exception {
        // 200 > 127, so it requires unsigned interpretation.
        Tensor t = Tensor.of(new byte[]{(byte) 200, 5}, 2).to(ScalarType.UInt8);
        String path = tempDir.resolve("u8.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.UInt8, loaded.dtype());
        // Cast to int32 to read unsigned values without sign-extension.
        Tensor asInt = loaded.to(ScalarType.Int32);
        assertArrayEquals(new int[]{200, 5}, asInt.intArray());

        t.close();
        loaded.close();
        asInt.close();
    }

    @Test
    public void testGivenUInt16TensorWhenRoundTrippedThenBitPatternPreserved() throws Exception {
        // 60000 > 32767, requiring unsigned interpretation.
        Tensor t = Tensor.of(new short[]{(short) 60000, 1}, 2).to(ScalarType.UInt16);
        String path = tempDir.resolve("u16.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.UInt16, loaded.dtype());
        Tensor asInt = loaded.to(ScalarType.Int32);
        assertArrayEquals(new int[]{60000, 1}, asInt.intArray());

        t.close();
        loaded.close();
        asInt.close();
    }

    @Test
    public void testGivenUInt32TensorWhenRoundTrippedThenBitPatternPreserved() throws Exception {
        // 3_000_000_000 > Integer.MAX_VALUE (2_147_483_647).
        // Represented as signed int32 it is -1_294_967_296 (same bit pattern 0xB2D05E00).
        Tensor t = Tensor.of(new int[]{(int) 3_000_000_000L, 100}, 2).to(ScalarType.UInt32);
        String path = tempDir.resolve("u32.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.UInt32, loaded.dtype());
        // Cast to int64 to read unsigned 32-bit values without truncation.
        Tensor asLong = loaded.to(ScalarType.Int64);
        assertArrayEquals(new long[]{3_000_000_000L, 100L}, asLong.longArray());

        t.close();
        loaded.close();
        asLong.close();
    }

    @Test
    public void testGivenUInt64TensorWhenRoundTrippedThenBitPatternPreserved() throws Exception {
        // Use a positive value that fits in a Java long (< 2^63).
        Tensor t = Tensor.of(new long[]{9_000_000_000L, 42L}, 2).to(ScalarType.UInt64);
        String path = tempDir.resolve("u64.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.UInt64, loaded.dtype());
        Tensor asLong = loaded.to(ScalarType.Int64);
        assertArrayEquals(new long[]{9_000_000_000L, 42L}, asLong.longArray());

        t.close();
        loaded.close();
        asLong.close();
    }

    // -----------------------------------------------------------------------
    // Boolean
    // -----------------------------------------------------------------------

    @Test
    public void testGivenBoolTensorWhenRoundTrippedThenValuesPreserved() throws Exception {
        boolean[] expected = {true, false, true, true, false};
        Tensor t = Tensor.of(expected, 5);
        String path = tempDir.resolve("bool.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.Bool, loaded.dtype());
        // byteArray() returns 1 for true, 0 for false.
        byte[] raw = loaded.byteArray();
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], raw[i] != 0, "element " + i);
        }

        t.close();
        loaded.close();
    }

    // -----------------------------------------------------------------------
    // Multi-dimensional shape
    // -----------------------------------------------------------------------

    @Test
    public void testGiven2DShapeWhenRoundTrippedThenShapePreserved() throws Exception {
        float[] data = {1f, 2f, 3f, 4f, 5f, 6f};
        Tensor t = Tensor.of(data, 2, 3);
        String path = tempDir.resolve("shape2d.safetensors").toString();
        new SafeTensors(Map.of("mat", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("mat");
        assertEquals(ScalarType.Float, loaded.dtype());
        assertArrayEquals(new long[]{2, 3}, loaded.shape());
        assertArrayEquals(data, loaded.floatArray(), 1e-6f);

        t.close();
        loaded.close();
    }

    // -----------------------------------------------------------------------
    // Multiple tensors in one file
    // -----------------------------------------------------------------------

    @Test
    public void testGivenMultipleTensorsWhenRoundTrippedThenAllPreserved() throws Exception {
        float[] fdata = {1f, 2f, 3f};
        long[] ldata = {10L, 20L, 30L};
        Tensor tf = Tensor.of(fdata, 3);
        Tensor tl = Tensor.of(ldata, 3);
        String path = tempDir.resolve("multi.safetensors").toString();
        new SafeTensors(Map.of("floats", tf, "longs", tl), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor lf = st.tensors().get("floats");
        Tensor ll = st.tensors().get("longs");
        assertNotNull(lf, "floats tensor must be present");
        assertNotNull(ll, "longs tensor must be present");
        assertEquals(ScalarType.Float, lf.dtype());
        assertEquals(ScalarType.Int64, ll.dtype());
        assertArrayEquals(fdata, lf.floatArray(), 1e-6f);
        assertArrayEquals(ldata, ll.longArray());

        tf.close(); tl.close();
        lf.close(); ll.close();
    }

    // -----------------------------------------------------------------------
    // Metadata
    // -----------------------------------------------------------------------

    @Test
    public void testGivenMetadataWhenRoundTrippedThenPreserved() throws Exception {
        float[] data = {1f};
        Tensor t = Tensor.of(data, 1);
        Map<String, String> meta = Map.of("format", "pt", "source", "unit-test");
        String path = tempDir.resolve("meta.safetensors").toString();
        new SafeTensors(Map.of("x", t), meta).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        assertEquals("pt", st.metadata().get("format"));
        assertEquals("unit-test", st.metadata().get("source"));

        t.close();
        st.tensors().values().forEach(Tensor::close);
    }

    @Test
    public void testGivenEmptyMetadataWhenRoundTrippedThenMetadataIsEmpty() throws Exception {
        Tensor t = Tensor.of(new float[]{1f}, 1);
        String path = tempDir.resolve("nometa.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        assertTrue(st.metadata().isEmpty());

        t.close();
        st.tensors().values().forEach(Tensor::close);
    }

    @Test
    public void testGivenMultiMegabyteFloatTensorWhenReadThenValuesMatch() throws Exception {
        // Exercises chunked FileChannel → LibTorch storage copies (1 MiB chunks).
        int n = 600_000; // 2.4 MiB of F32
        float[] data = new float[n];
        for (int i = 0; i < n; i++) {
            data[i] = i * 0.001f;
        }
        Tensor t = Tensor.of(data, n);
        String path = tempDir.resolve("large_f32.safetensors").toString();
        new SafeTensors(Map.of("x", t), Map.of()).write(path);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.Float, loaded.dtype());
        assertArrayEquals(new long[]{n}, loaded.shape());
        assertEquals(data[0], loaded.getFloat(0), 1e-5);
        assertEquals(data[n / 2], loaded.getFloat(n / 2), 1e-5);
        assertEquals(data[n - 1], loaded.getFloat(n - 1), 1e-5);

        t.close();
        loaded.close();
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "smile.test.largeSafetensors", matches = "true")
    public void testGivenBf16TensorExceedingIntMaxBytesWhenReadThenShapeIsCorrect()
            throws Exception {
        // ~2.000000001 GiB of BF16 — needs ~2 GiB RAM and a rebuilt smile_torch.
        long byteLength = (long) Integer.MAX_VALUE + 1024L;
        long numel = byteLength / 2;
        String path = tempDir.resolve("huge_bf16.safetensors").toString();
        writeSparseSafetensors(path, "x", "BF16", new long[]{numel}, byteLength);

        SafeTensors st = SafeTensors.read(path, Device.CPU());
        Tensor loaded = st.tensors().get("x");
        assertEquals(ScalarType.BFloat16, loaded.dtype());
        assertArrayEquals(new long[]{numel}, loaded.shape());
        loaded.close();
    }

    /**
     * Writes a safetensors file whose data region is sparse (mostly holes) so
     * oversized tensors can be tested without writing multi-gigabyte payloads.
     */
    private static void writeSparseSafetensors(String path, String name, String dtype,
                                               long[] shape, long dataBytes) throws Exception {
        StringBuilder shapeJson = new StringBuilder("[");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) shapeJson.append(',');
            shapeJson.append(shape[i]);
        }
        shapeJson.append(']');
        String headerJson = "{\"" + name + "\":{\"dtype\":\"" + dtype
                + "\",\"shape\":" + shapeJson
                + ",\"data_offsets\":[0," + dataBytes + "]}}";
        byte[] header = headerJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (var channel = java.nio.channels.FileChannel.open(
                java.nio.file.Path.of(path),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE)) {
            var lenBuf = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            lenBuf.putLong(header.length).flip();
            channel.write(lenBuf);
            channel.write(java.nio.ByteBuffer.wrap(header));
            long dataStart = 8L + header.length;
            if (dataBytes > 0) {
                var one = java.nio.ByteBuffer.wrap(new byte[]{0});
                channel.write(one, dataStart + dataBytes - 1);
            }
        }
    }
}
