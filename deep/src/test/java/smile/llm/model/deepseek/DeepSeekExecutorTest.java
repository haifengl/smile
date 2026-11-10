/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.deepseek;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in ({@code DEEPSEEK_EXECUTOR=cpu|cuda|cuda<ranks>}, needs the 4-layer slice shards): requests served by {@link DeepSeek}
 * must not influence each other: two requests one step apart that share batched decode calls get the same logits as a
 * request decoded alone.
 *
 * @author Haifeng Li
 */
public class DeepSeekExecutorTest {
    /** The oracle's decode inputs for the slice prompt (teacher forcing keeps every engram row inside the fixture). */
    private static final int[] FORCED = {84616, 10557, 121804};

    private static double relL2(float[] a, float[] b) {
        double num = 0;
        double den = 0;
        for (int i = 0; i < a.length; i++) {
            num += (double) (a[i] - b[i]) * (a[i] - b[i]);
            den += (double) b[i] * b[i];
        }
        return Math.sqrt(num / den);
    }

    private static float[] cpu(Tensor logits) {
        return Dsv.host(logits).floatArray();
    }

    /**
     * Two requests holding the same sequence, B one step behind A, decoded in shared batches. Returns the logits after
     * the prompt and after each forced token: {@code [0]} for A, {@code [1]} for B (B's step 0 is its prefill).
     * With {@code swap} the rows of every batch are in the other order.
     */
    private static float[][][] schedule(DeepSeek ex, int[] a, boolean swap) {
        int cap = a.length + FORCED.length;
        int idA = ex.bind(a, cap);
        int idB = ex.bind(a, cap);
        float[][][] out = new float[2][FORCED.length + 1][];
        out[0][0] = cpu(ex.prefill(idA, a, 0));
        out[1][0] = cpu(ex.prefill(idB, a, 0));
        out[0][1] = cpu(ex.decodeStep(new int[]{idA}, new int[]{FORCED[0]}, new int[]{a.length}));
        for (int i = 1; i < FORCED.length; i++) {
            int[] ids = {idA, idB};
            int[] tok = {FORCED[i], FORCED[i - 1]};
            int[] pos = {a.length + i, a.length + i - 1};
            if (swap) {
                ids = new int[]{idB, idA};
                tok = new int[]{FORCED[i - 1], FORCED[i]};
                pos = new int[]{a.length + i - 1, a.length + i};
            }
            float[] flat = cpu(ex.decodeStep(ids, tok, pos));
            int v = flat.length / 2;
            out[swap ? 1 : 0][i + (swap ? 0 : 1)] = java.util.Arrays.copyOfRange(flat, 0, v);
            out[swap ? 0 : 1][i + (swap ? 1 : 0)] = java.util.Arrays.copyOfRange(flat, v, 2 * v);
        }
        out[1][FORCED.length] = cpu(ex.decodeStep(new int[]{idB}, new int[]{FORCED[FORCED.length - 1]},
                new int[]{a.length + FORCED.length - 1}));
        ex.evict(idA);
        ex.evict(idB);
        return out;
    }

    @Test
    public void testGivenTwoRequestsAtDifferentStepsThenInterleavedLogitsEqualSolo() throws Exception {
        String mode = System.getenv().getOrDefault("DEEPSEEK_EXECUTOR", "");
        Assumptions.assumeTrue(mode.equals("cpu") || mode.startsWith("cuda"), "DEEPSEEK_EXECUTOR not set");
        Path dir = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"));
        Path fx = Path.of(System.getenv().getOrDefault("DEEPSEEK_FIXTURES", "/raid/haili/dsv4/fixtures"));
        Assumptions.assumeTrue(Files.exists(fx.resolve("slice4_prefill.safetensors")), "no fixture");
        long[] idv = SafeTensors.read(fx.resolve("slice4_prefill.safetensors").toString(), Device.CPU()).tensors()
                .get("ids").longArray();
        int[] a = new int[idv.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = (int) idv[i];
        }
        var r = SafeTensors.read(fx.resolve("engram_rows_layer1.safetensors").toString(), Device.CPU()).tensors();
        DeepSeekEngram.Rows rows = new DeepSeekEngram.InMemoryRows(r.get("rows"), r.get("w"), r.get("s"));
        int ranks = mode.startsWith("cuda") && mode.length() > 4 ? Integer.parseInt(mode.substring(4)) : 1;
        byte[] devices = null;
        if (mode.startsWith("cuda")) {
            devices = new byte[ranks];
            for (int i = 0; i < ranks; i++) {
                devices[i] = (byte) i;
            }
        }
        float tol = ranks > 1 ? 1e-2f : 1e-5f;   // NCCL may sum the partial expert outputs in another order
        try (DeepSeek ex = DeepSeek.build(dir.toString(), 4, 256, devices, l -> rows)) {
            // solo: logits after the prompt and after each forced token
            float[][] solo = new float[FORCED.length + 1][];
            int id = ex.bind(a, a.length + FORCED.length);
            solo[0] = cpu(ex.prefill(id, a, 0));
            for (int i = 0; i < FORCED.length; i++) {
                solo[i + 1] = cpu(ex.decodeStep(new int[]{id}, new int[]{FORCED[i]}, new int[]{a.length + i}));
            }
            ex.evict(id);

            // rows are independent: the same schedule with the rows in the other order gives the same logits
            float[][][] fwd = schedule(ex, a, false);
            float[][][] rev = schedule(ex, a, true);
            for (int step = 1; step <= FORCED.length; step++) {
                System.out.printf("step %d: A vs solo %.5f, B vs solo %.5f; swapped order: A %.6f, B %.6f%n", step,
                        relL2(fwd[0][step], solo[step]), relL2(fwd[1][step], solo[step]),
                        relL2(rev[0][step], fwd[0][step]), relL2(rev[1][step], fwd[1][step]));
                // batch-size noise of the fp8 activation quantization (see DeepSeekExecutorTest#schedule) stays small
                assertTrue(relL2(fwd[0][step], solo[step]) < 0.2, "A step " + step + " vs solo");
                assertTrue(relL2(fwd[1][step], solo[step]) < 0.2, "B step " + step + " vs solo");
                assertTrue(relL2(rev[0][step], fwd[0][step]) < 1e-4, "A step " + step + " swapped");
                assertTrue(relL2(rev[1][step], fwd[1][step]) < 1e-4, "B step " + step + " swapped");
            }
            assertArrayEquals(solo[0], fwd[0][0], tol, "A prefill");

            // the same prompt through the engine-facing chunked entry point
            int idC = ex.bind(a, a.length + FORCED.length);
            assertNull(ex.prefillChunk(idC, a, 0, 16));
            assertArrayEquals(solo[0], cpu(ex.prefillChunk(idC, a, 16, a.length)), tol, "chunked prefill");
        }
    }

    /** A batch of one decode row is the legacy single-sequence path, bit for bit (same shapes, same kernels). */
    @Test
    public void testGivenBatchOfOneThenEqualsLegacyForward() throws Exception {
        String mode = System.getenv().getOrDefault("DEEPSEEK_EXECUTOR", "");
        Assumptions.assumeTrue(mode.equals("cpu") || mode.startsWith("cuda"), "DEEPSEEK_EXECUTOR not set");
        Path dir = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"));
        Path fx = Path.of(System.getenv().getOrDefault("DEEPSEEK_FIXTURES", "/raid/haili/dsv4/fixtures"));
        Assumptions.assumeTrue(Files.exists(fx.resolve("slice4_prefill.safetensors")), "no fixture");
        long[] idv = SafeTensors.read(fx.resolve("slice4_prefill.safetensors").toString(), Device.CPU()).tensors()
                .get("ids").longArray();
        int[] a = new int[idv.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = (int) idv[i];
        }
        var r = SafeTensors.read(fx.resolve("engram_rows_layer1.safetensors").toString(), Device.CPU()).tensors();
        DeepSeekEngram.Rows rows = new DeepSeekEngram.InMemoryRows(r.get("rows"), r.get("w"), r.get("s"));
        boolean gpu = mode.startsWith("cuda");
        Dsv.setDevice(gpu ? Device.CUDA((byte) 0) : Device.CPU());
        Dsv.marlin = gpu;
        try {
            DeepSeekModelArgs args = DeepSeekModelArgs.load(dir.resolve("config.json").toFile()).slice(4).withMaxSeqLen(256);
            try (DeepSeekModel m = new DeepSeekModel(args, dir, l -> rows)) {
                DeepSeekModel.Session s1 = m.newSession();
                DeepSeekModel.Session s2 = m.newSession();
                m.use(s1);
                m.forward(a, 0, null);
                m.use(s2);
                m.forward(a, 0, null);
                for (int i = 0; i < FORCED.length; i++) {
                    m.use(s1);
                    float[] legacy = cpu(m.forward(new int[]{FORCED[i]}, a.length + i, null));
                    float[] batched = cpu(m.forwardBatch(new int[]{FORCED[i]}, new int[]{a.length + i},
                            new DeepSeekModel.Session[]{s2}));
                    assertArrayEquals(legacy, batched, 1e-5f, "step " + (i + 1));
                }
                s1.close();
                s2.close();
            }
        } finally {
            Dsv.setDevice(Device.CPU());
            Dsv.marlin = false;
        }
    }

    /**
     * Two concurrent requests through the continuous-batching engine, greedy, on the CPU (where the slice follows the
     * oracle's trajectory): each must produce the oracle's tokens.
     */
    @Test
    public void testGivenEngineThenGreedyTokensEqualOracle() throws Exception {
        Assumptions.assumeTrue("cpu".equals(System.getenv("DEEPSEEK_EXECUTOR")), "DEEPSEEK_EXECUTOR=cpu not set");
        Path dir = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"));
        Path fx = Path.of(System.getenv().getOrDefault("DEEPSEEK_FIXTURES", "/raid/haili/dsv4/fixtures"));
        Assumptions.assumeTrue(Files.exists(fx.resolve("slice4_meta.json")), "no fixture");
        var meta = new tools.jackson.databind.ObjectMapper().readTree(fx.resolve("slice4_meta.json").toFile());
        int[] prompt = new int[meta.get("prompt_ids").size()];
        for (int i = 0; i < prompt.length; i++) {
            prompt[i] = meta.get("prompt_ids").get(i).asInt();
        }
        int[] expected = new int[FORCED.length + 1];
        for (int i = 0; i < FORCED.length; i++) {
            expected[i] = meta.get("decode_inputs").get(i).asInt();
        }
        expected[FORCED.length] = meta.get("next_after").asInt();
        var r = SafeTensors.read(fx.resolve("engram_rows_layer1.safetensors").toString(), Device.CPU()).tensors();
        DeepSeekEngram.Rows rows = new DeepSeekEngram.InMemoryRows(r.get("rows"), r.get("w"), r.get("s"));
        try (DeepSeek ex = DeepSeek.build(dir.toString(), 4, 256, null, l -> rows)) {
            var engine = new smile.llm.engine.InferenceEngine(ex, 2, 2, 16, 0);
            var h1 = engine.submit(smile.llm.engine.GenerationRequest.ofTokens(prompt, expected.length, 0.0, 1.0, false, 0, null));
            var h2 = engine.submit(smile.llm.engine.GenerationRequest.ofTokens(prompt, expected.length, 0.0, 1.0, false, 0, null));
            assertArrayEquals(expected, h1.future().get(30, java.util.concurrent.TimeUnit.MINUTES).completionTokens());
            assertArrayEquals(expected, h2.future().get(30, java.util.concurrent.TimeUnit.MINUTES).completionTokens());
        }
    }
}
