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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checkpoint-loading tests: a tiny random BF16-style checkpoint is written to disk in HuggingFace
 * layout and loaded through {@link MiniMax#build}.
 *
 * @author Haifeng Li
 */
public class MiniMaxBuildTest {
    private static final String CONFIG = "deep/src/test/resources/minimax/config_tiny.json";
    private static final String HF = "language_model.model.";

    @TempDir
    Path dir;

    static long[] shapeOf(String name, MiniMaxModelArgs a) {
        long d = a.dim();
        long qOut = (long) a.numHeads() * a.headDim();
        long kvOut = (long) a.numKvHeads() * a.headDim();
        long idx = (long) a.numIndexHeads() * a.indexDim();
        if (name.equals("embed_tokens.weight") || name.equals("lm_head.weight")) return new long[]{a.vocabSize(), d};
        if (name.equals("norm.weight") || name.endsWith("layernorm.weight")) return new long[]{d};
        if (name.endsWith("self_attn.q_proj.weight")) return new long[]{qOut, d};
        if (name.endsWith("self_attn.k_proj.weight") || name.endsWith("self_attn.v_proj.weight")) return new long[]{kvOut, d};
        if (name.endsWith("self_attn.o_proj.weight")) return new long[]{d, qOut};
        if (name.endsWith("q_norm.weight") || name.endsWith("k_norm.weight")) {
            return new long[]{name.contains("index_") ? a.indexDim() : a.headDim()};
        }
        if (name.contains("index_q_proj")) return new long[]{idx, d};
        if (name.contains("index_k_proj")) return new long[]{a.indexDim(), d};
        if (name.endsWith("moe.gate.weight")) return new long[]{a.numExperts(), d};
        if (name.endsWith("e_score_correction_bias")) return new long[]{a.numExperts()};
        long inter = name.contains(".mlp.") ? a.denseIntermediateSize()
                : name.contains("shared_experts") ? a.sharedIntermediateSize() : a.expertIntermediateSize();
        return name.endsWith(".w2.weight") ? new long[]{d, inter} : new long[]{inter, d};
    }

    /** SMILE parameter name to HuggingFace tensor name. */
    static String toHf(String n) {
        if (n.equals("embed_tokens.weight") || n.equals("norm.weight")) return HF + n;
        if (n.equals("lm_head.weight")) return "language_model.lm_head.weight";
        String h = n.replace(".mlp.w1.", ".mlp.gate_proj.").replace(".mlp.w2.", ".mlp.down_proj.")
                .replace(".mlp.w3.", ".mlp.up_proj.")
                .replace(".moe.shared_experts.w1.", ".block_sparse_moe.shared_experts.gate_proj.")
                .replace(".moe.shared_experts.w2.", ".block_sparse_moe.shared_experts.down_proj.")
                .replace(".moe.shared_experts.w3.", ".block_sparse_moe.shared_experts.up_proj.")
                .replace(".moe.", ".block_sparse_moe.");
        return HF + h;
    }

    static Map<String, float[]> randomWeights(MiniMaxModelArgs a) {
        Random rnd = new Random(7);
        Map<String, float[]> out = new LinkedHashMap<>();
        for (String n : MiniMaxWeightNames.expectedNames(a)) {
            long[] shape = shapeOf(n, a);
            int count = 1;
            for (long s : shape) count *= (int) s;
            float[] v = new float[count];
            boolean norm = n.endsWith("norm.weight") || n.endsWith("layernorm.weight");
            for (int i = 0; i < count; i++) {
                v[i] = norm ? 0.1f * (float) rnd.nextGaussian() : 0.05f * (float) rnd.nextGaussian();
            }
            out.put(n, v);
        }
        return out;
    }

    /** Minimal safetensors writer (float32 only). */
    private static void writeSafetensors(Path file, Map<String, float[]> tensors, MiniMaxModelArgs a)
            throws IOException {
        StringBuilder header = new StringBuilder("{");
        long offset = 0;
        boolean first = true;
        for (var e : tensors.entrySet()) {
            long[] shape = shapeOf(e.getKey(), a);
            long bytes = 4L * e.getValue().length;
            if (!first) header.append(',');
            first = false;
            header.append('"').append(toHf(e.getKey())).append("\":{\"dtype\":\"F32\",\"shape\":")
                    .append(java.util.Arrays.toString(shape).replace(" ", ""))
                    .append(",\"data_offsets\":[").append(offset).append(',').append(offset + bytes).append("]}");
            offset += bytes;
        }
        header.append('}');
        byte[] h = header.toString().getBytes(StandardCharsets.UTF_8);
        int pad = (8 - h.length % 8) % 8;
        ByteBuffer buf = ByteBuffer.allocate((int) (8 + h.length + pad + offset)).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(h.length + pad);
        buf.put(h);
        for (int i = 0; i < pad; i++) buf.put((byte) ' ');
        for (float[] v : tensors.values()) {
            for (float x : v) buf.putFloat(x);
        }
        Files.write(file, buf.array());
    }

    static String tokenizerJson() {
        StringBuilder vocab = new StringBuilder();
        int id = 0;
        for (char c = 'a'; c <= 'z'; c++) vocab.append('"').append(c).append("\":").append(id++).append(',');
        for (char c = '0'; c <= '9'; c++) vocab.append('"').append(c).append("\":").append(id++).append(',');
        // Fill every other id below vocab_size (except the specials at 90-92) so random weights
        // can only generate decodable tokens.
        for (int fill = id; fill < 100; fill++) {
            if (fill >= 90 && fill <= 92) continue;
            vocab.append("\"q").append(fill).append("\":").append(fill).append(',');
        }
        vocab.setLength(vocab.length() - 1);
        return "{\"model\":{\"type\":\"BPE\",\"vocab\":{" + vocab + "},\"merges\":[]},"
                + "\"added_tokens\":[{\"id\":90,\"content\":\"]!p~[\",\"special\":true},"
                + "{\"id\":91,\"content\":\"]~b]\",\"special\":true},"
                + "{\"id\":92,\"content\":\"[e~[\",\"special\":true}]}";
    }

    private void writeCheckpoint(Map<String, float[]> weights, boolean quantized) throws IOException {
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(CONFIG, 2, 0);
        String config = Files.readString(Path.of(CONFIG));
        if (quantized) {
            config = config.replaceFirst("\\{", "{\"quantization_config\":{\"quant_method\":\"mxfp8\"},");
        }
        Files.writeString(dir.resolve("config.json"), config);
        Files.writeString(dir.resolve("tokenizer.json"), tokenizerJson());
        writeSafetensors(dir.resolve("model.safetensors"), weights, a);
    }

    @Test
    public void testGivenExpectedNamesWhenStrictLoadedThenEveryRegisteredParameterIsCovered() throws IOException {
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(CONFIG, 2, 0);
        MiniMaxModel model = new MiniMaxModel(a);
        model.to(Device.CPU());
        Map<String, Tensor> dict = new HashMap<>();
        try (var noGrad = Tensor.noGradGuard()) {
            var stacks = new LinkedHashMap<String, float[]>();
            var stackShapes = new HashMap<String, long[]>();
            var expert = java.util.regex.Pattern.compile("^(layers\\.\\d+\\.moe)\\.experts\\.(\\d+)\\.w([123])\\.weight$");
            for (var e : randomWeights(a).entrySet()) {
                var m = expert.matcher(e.getKey());
                if (!m.matches()) {
                    dict.put(e.getKey(), Tensor.of(e.getValue(), shapeOf(e.getKey(), a)));
                    continue;
                }
                // Routed experts are stacked [E, ...] under experts_w1/2/3.
                String stacked = m.group(1) + ".experts_w" + m.group(3);
                long[] shape = shapeOf(e.getKey(), a);
                stackShapes.put(stacked, shape);
                float[] all = stacks.computeIfAbsent(stacked, k -> new float[a.numExperts() * e.getValue().length]);
                System.arraycopy(e.getValue(), 0, all, Integer.parseInt(m.group(2)) * e.getValue().length, e.getValue().length);
            }
            for (var e : stacks.entrySet()) {
                long[] shape = stackShapes.get(e.getKey());
                long[] full = new long[shape.length + 1];
                full[0] = a.numExperts();
                System.arraycopy(shape, 0, full, 1, shape.length);
                dict.put(e.getKey(), Tensor.of(e.getValue(), full));
            }
            // strict=true fails on a missing registered parameter or an unknown provided name.
            model.loadStateDict(dict, true);
        } finally {
            dict.values().forEach(Tensor::close);
        }
    }

    @Test
    public void testGivenHuggingFaceCheckpointWhenBuiltThenGreedyGenerationIsDeterministic() throws IOException {
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(CONFIG, 2, 0);
        writeCheckpoint(randomWeights(a), false);
        try (MiniMax m = MiniMax.build(dir.toString(), 2, 0, (byte) -1, 0, 1)) {
            int[] prompt = m.encode("abc123");
            assertTrue(prompt.length > 0);
            ChatCompletion first = m.generate(prompt, 6, 0.0, 0.9, false, 0, null);
            ChatCompletion second = m.generate(prompt, 6, 0.0, 0.9, false, 0, null);
            assertTrue(first.completionTokens().length <= 6);
            assertNotNull(m.network().layers().get(3).moe().routerWeight, "router kept in float32");
            assertEquals(smile.deep.tensor.ScalarType.Float, m.network().layers().get(3).moe().routerBias.dtype());
            assertArrayEquals(first.completionTokens(), second.completionTokens());
        }
    }

    @Test
    public void testGivenMissingWeightWhenBuiltThenFailsLoudly() throws IOException {
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(CONFIG, 2, 0);
        Map<String, float[]> weights = randomWeights(a);
        weights.remove("layers.3.moe.experts.5.w2.weight");
        writeCheckpoint(weights, false);
        IOException e = assertThrows(IOException.class,
                () -> MiniMax.build(dir.toString(), 2, 0, (byte) -1, 0, 1));
        assertTrue(e.getMessage().contains("layers.3.moe.experts.5.w2.weight"), e.getMessage());
    }

    @Test
    public void testGivenQuantizedConfigWhenBuiltThenRejected() throws IOException {
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(CONFIG, 2, 0);
        writeCheckpoint(randomWeights(a), true);
        IOException e = assertThrows(IOException.class,
                () -> MiniMax.build(dir.toString(), 2, 0, (byte) -1, 0, 1));
        assertTrue(e.getMessage().contains("mxfp8"), e.getMessage());
    }

    @Test
    public void testGivenCudaWhenBuiltThenMixedBatchDecodeRunsOnDevice() throws IOException {
        assumeTrue(smile.torch.smile_torch_h.smile_cuda_is_available() != 0, "CUDA not available");
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(CONFIG, 2, 0);
        writeCheckpoint(randomWeights(a), false);
        try (MiniMax m = MiniMax.build(dir.toString(), 2, 0, (byte) 0, 0, 1)) {
            int[] p1 = m.encode("abc123");
            int[] p2 = m.encode("zyx");
            ChatCompletion c1 = m.generate(p1, 5, 0.0, 0.9, false, 0, null);
            ChatCompletion c2 = m.generate(p2, 5, 0.0, 0.9, false, 0, null);
            assertEquals(c1.completionTokens().length > 0, true);
            assertArrayEquals(c1.completionTokens(), m.generate(p1, 5, 0.0, 0.9, false, 0, null).completionTokens());
            assertNotNull(c2);

            // Two requests at different positions decoded in one ragged step.
            int id1 = m.bind(p1, 16);
            int id2 = m.bind(p2, 16);
            try (Tensor l1 = m.prefill(id1, p1, 0);
                 Tensor l2 = m.prefill(id2, p2, 0);
                 Tensor step = m.decodeStep(new int[]{id1, id2}, new int[]{3, 4},
                         new int[]{p1.length, p2.length})) {
                assertArrayEquals(new long[]{2, a.vocabSize()}, step.shape());
            } finally {
                m.evict(id1);
                m.evict(id2);
            }
        }
    }

    @Test
    public void testGivenRequestsWithDifferentCapacitiesWhenDecodedTogetherThenMatchesSingleDecodes() throws IOException {
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(CONFIG, 4, 0);
        writeCheckpoint(randomWeights(a), false);
        try (MiniMax m = MiniMax.build(dir.toString(), 4, 0, (byte) -1, 0, 1)) {
            int[] p1 = {3, 1, 4, 1, 5};
            int[] p2 = {9, 2, 6, 5, 3, 5, 8, 9, 7, 9, 3, 2};
            int[] tokens = {7, 11};

            // Batched: the first request reserves 8 slots, the second 40.
            int i1 = m.bind(p1, 8);
            int i2 = m.bind(p2, 40);
            float[] batched;
            try (var noGrad = Tensor.noGradGuard()) {
                m.prefill(i1, p1, 0).close();
                m.prefill(i2, p2, 0).close();
                try (Tensor logits = m.decodeStep(new int[]{i1, i2}, tokens, new int[]{p1.length, p2.length})) {
                    batched = logits.copy().floatArray();
                }
            } finally {
                m.evict(i1);
                m.evict(i2);
            }

            // Alone, same capacities.
            int[][] prompts = {p1, p2};
            int[] caps = {8, 40};
            int vocab = a.vocabSize();
            for (int r = 0; r < 2; r++) {
                int id = m.bind(prompts[r], caps[r]);
                try (var noGrad = Tensor.noGradGuard()) {
                    m.prefill(id, prompts[r], 0).close();
                    try (Tensor logits = m.decodeStep(new int[]{id}, new int[]{tokens[r]}, new int[]{prompts[r].length})) {
                        float[] single = logits.copy().floatArray();
                        for (int v = 0; v < vocab; v++) {
                            assertEquals(single[v], batched[r * vocab + v], 1e-4f, "request " + r + " logit " + v);
                        }
                    }
                } finally {
                    m.evict(id);
                }
            }
        }
    }
}
