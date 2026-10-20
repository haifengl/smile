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
package smile.llm.model.minimax;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import smile.deep.tensor.Tensor;
import smile.llm.quant.MxFp8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * An MXFP8 checkpoint must load to exactly the weights its dense dequantization would give: the same
 * random FP8 bytes and E8M0 scales are written once as MXFP8 and once as the expanded float32 values.
 *
 * @author Haifeng Li
 */
public class MiniMaxMxfp8LoadTest {
    private static final String CONFIG = "deep/src/test/resources/minimax/config_tiny.json";
    private static final Pattern QUANTIZED = Pattern.compile(
            ".*(_proj|\\.mlp\\.w[123]|\\.experts\\.\\d+\\.w[123]|shared_experts\\.w[123])\\.weight$");

    @TempDir
    Path root;

    /** One safetensors entry. */
    private record Entry(String dtype, long[] shape, byte[] data) { }

    /** Decodes an E4M3FN byte (NaN excluded by the generator). */
    private static float decodeE4m3(int b) {
        int sign = (b >> 7) & 1;
        int exp = (b >> 3) & 0xF;
        int man = b & 0x7;
        double v = exp == 0 ? Math.scalb(man / 8.0, -6) : Math.scalb(1.0 + man / 8.0, exp - 7);
        return (float) (sign == 1 ? -v : v);
    }

    private static void write(Path file, Map<String, Entry> tensors) throws IOException {
        StringBuilder header = new StringBuilder("{");
        long offset = 0;
        boolean first = true;
        for (var e : tensors.entrySet()) {
            if (!first) header.append(',');
            first = false;
            Entry t = e.getValue();
            header.append('"').append(e.getKey()).append("\":{\"dtype\":\"").append(t.dtype()).append("\",\"shape\":")
                    .append(java.util.Arrays.toString(t.shape()).replace(" ", ""))
                    .append(",\"data_offsets\":[").append(offset).append(',').append(offset + t.data().length).append("]}");
            offset += t.data().length;
        }
        header.append('}');
        byte[] h = header.toString().getBytes(StandardCharsets.UTF_8);
        int pad = (8 - h.length % 8) % 8;
        ByteBuffer buf = ByteBuffer.allocate((int) (8 + h.length + pad + offset)).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(h.length + pad);
        buf.put(h);
        for (int i = 0; i < pad; i++) buf.put((byte) ' ');
        for (Entry t : tensors.values()) buf.put(t.data());
        Files.write(file, buf.array());
    }

    private static byte[] floats(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(4 * v.length).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : v) b.putFloat(x);
        return b.array();
    }

    @Test
    public void testGivenMxfp8CheckpointWhenLoadedThenEqualsDenseDequantizedCheckpoint() throws IOException {
        MiniMaxModelArgs args = MiniMaxModelArgs.fromHuggingFace(CONFIG, 2, 0);
        Random rnd = new Random(21);
        Map<String, Entry> quantized = new LinkedHashMap<>();
        Map<String, Entry> dense = new LinkedHashMap<>();
        List<String> scaleKeys = new ArrayList<>();
        for (String name : MiniMaxWeightNames.expectedNames(args)) {
            long[] shape = MiniMaxBuildTest.shapeOf(name, args);
            int count = 1;
            for (long s : shape) count *= (int) s;
            String hf = MiniMaxBuildTest.toHf(name);
            if (!QUANTIZED.matcher(name).matches()) {
                float[] v = new float[count];
                boolean norm = name.endsWith("norm.weight") || name.endsWith("layernorm.weight");
                for (int i = 0; i < count; i++) v[i] = (norm ? 0.1f : 0.05f) * (float) rnd.nextGaussian();
                Entry e = new Entry("F32", shape, floats(v));
                quantized.put(hf, e);
                dense.put(hf, e);
                continue;
            }
            int n = (int) shape[0];
            int k = (int) shape[1];
            byte[] fp8 = new byte[count];
            for (int i = 0; i < count; i++) {
                int b;
                do {
                    b = rnd.nextInt(256);
                } while ((b & 0x7F) == 0x7F);
                // Keep magnitudes small so the tiny model stays well-conditioned.
                fp8[i] = (byte) ((b & 0x80) | (b & 0x3F));
            }
            byte[] codes = new byte[n * (k / MxFp8.BLOCK)];
            for (int i = 0; i < codes.length; i++) codes[i] = (byte) (118 + rnd.nextInt(8));
            float[] expanded = new float[count];
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < k; c++) {
                    int code = codes[r * (k / MxFp8.BLOCK) + c / MxFp8.BLOCK] & 0xFF;
                    expanded[r * k + c] = decodeE4m3(fp8[r * k + c] & 0xFF) * MxFp8.e8m0ToFloat(code);
                }
            }
            quantized.put(hf, new Entry("F8_E4M3", shape, fp8));
            quantized.put(hf + "_scale_inv", new Entry("U8", new long[]{n, k / MxFp8.BLOCK}, codes));
            dense.put(hf, new Entry("F32", shape, floats(expanded)));
        }

        Path q = Files.createDirectory(root.resolve("mxfp8"));
        Path d = Files.createDirectory(root.resolve("dense"));
        String config = Files.readString(Path.of(CONFIG));
        Files.writeString(q.resolve("config.json"), config.replaceFirst("\\{",
                "{\"quantization_config\":{\"quant_method\":\"mxfp8\",\"weight_block_size\":[1,32]},"));
        Files.writeString(d.resolve("config.json"), config);
        for (Path dir : List.of(q, d)) {
            Files.writeString(dir.resolve("tokenizer.json"), MiniMaxBuildTest.tokenizerJson());
        }
        write(q.resolve("model.safetensors"), quantized);
        write(d.resolve("model.safetensors"), dense);

        int[] ids = {3, 1, 4, 1, 5, 9, 2, 6};
        float[] fromQuantized;
        float[] fromDense;
        try (MiniMax m = MiniMax.build(q.toString(), 2, 0, (byte) -1, 0, 1)) {
            fromQuantized = logits(m, ids);
        }
        try (MiniMax m = MiniMax.build(d.toString(), 2, 0, (byte) -1, 0, 1)) {
            fromDense = logits(m, ids);
        }
        assertArrayEquals(fromDense, fromQuantized, 0.0f, "MXFP8 load must equal the dense dequantized load");
    }

    private static float[] logits(MiniMax m, int[] ids) {
        int id = m.bind(ids, ids.length);
        try (var noGrad = Tensor.noGradGuard();
             Tensor all = m.forwardAllLogits(id, ids)) {
            return all.copy().floatArray();
        } finally {
            m.evict(id);
        }
    }

    @Test
    public void testGivenOtherQuantizationWhenBuiltThenRejected() throws IOException {
        Path dir = Files.createDirectory(root.resolve("fp8"));
        String config = Files.readString(Path.of(CONFIG));
        Files.writeString(dir.resolve("config.json"), config.replaceFirst("\\{",
                "{\"quantization_config\":{\"quant_method\":\"fp8\",\"weight_block_size\":[128,128]},"));
        IOException e = assertThrows(IOException.class,
                () -> MiniMax.build(dir.toString(), 1, 0, (byte) -1, 0, 1));
        assertTrue(e.getMessage().contains("only MXFP8"), e.getMessage());
    }
}
