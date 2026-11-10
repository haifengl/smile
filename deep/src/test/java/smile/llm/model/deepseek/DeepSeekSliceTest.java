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
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The 4-layer slice of DeepSeek-V4.1-Flash-1017 (layers 0-3: window attention, an engram layer, a compressor and
 * indexer layer) against the traces of the pure-PyTorch port of the reference model.
 *
 * <p>Opt-in: needs the slice shards in {@code DEEPSEEK_DIR} (default {@code /raid/haili/dsv4/flash_raw}), the fixtures
 * in {@code DEEPSEEK_FIXTURES} and the native library. Both paths use bf16 activations with fp8/fp4 fake
 * quantization, which turns last-bit differences of a matrix product into occasional one-step quantization flips, so
 * the comparison is a relative error per tensor, not bit equality.
 */
public class DeepSeekSliceTest {
    private static double rel(Tensor a, Tensor b) {
        assertArrayEquals(b.shape(), a.shape(), "shape");
        Tensor d = Aten.one("aten::sub.Tensor", DeepSeekOps.f32(a), DeepSeekOps.f32(b));
        double num = Aten.one("aten::linalg_vector_norm", d, 2.0).doubleValue();
        double den = Aten.one("aten::linalg_vector_norm", DeepSeekOps.f32(b), 2.0).doubleValue();
        return num / den;
    }

    @Test
    public void testGivenShortPromptThenLayerTracesAndLogitsMatchOracle() throws Exception {
        check("slice4", 4, 256);
    }

    @Test
    public void testGiven25LayersAnd560TokensThenRatio1LayersAndCandidatesMatchOracle() throws Exception {
        // layers 0-24: ratio-2 and ratio-1 compressors, the candidate source (20) and its first user (24), both
        // engram layers; 560 tokens exceed index_topk (512) so the ratio-1 indexers really select
        Assumptions.assumeTrue(Files.exists(Path.of(System.getenv().getOrDefault("DEEPSEEK_FIXTURES", "/raid/haili/dsv4/fixtures"),
                "slice25_prefill.safetensors")), "no 25-layer fixture");
        check("slice25", 25, 576);
    }

    @Test
    public void testGivenPromptLongerThanWindowThenRingWrapsAndTracesMatchOracle() throws Exception {
        check("slice4L", 4, 512);   // 151 tokens: the 128-entry window wraps, 75 compressed positions
    }

    @Test
    public void testGivenShortPromptOnCudaWithMarlinExpertsThenLogitsTrackOracle() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv().getOrDefault("DEEPSEEK_CUDA", "false")), "DEEPSEEK_CUDA not set");
        check("slice4", 4, 256, true);
    }

    private void check(String name, int layers, int maxSeq) throws Exception {
        check(name, layers, maxSeq, false);
    }

    private void check(String name, int layers, int maxSeq, boolean gpu) throws Exception {
        Dsv.setDevice(gpu ? Device.CUDA() : Device.CPU());
        Dsv.marlin = gpu && Boolean.parseBoolean(System.getenv().getOrDefault("DEEPSEEK_MARLIN", "true"));
        try {
            run(name, layers, maxSeq, Double.parseDouble(System.getenv().getOrDefault("DEEPSEEK_TOL", gpu ? "0.05" : "1e-3")), gpu);
        } finally {
            Dsv.setDevice(Device.CPU());
            Dsv.marlin = false;
        }
    }

    private void run(String name, int layers, int maxSeq, double tol, boolean gpu) throws Exception {
        Path dir = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"));
        Path fx = Path.of(System.getenv().getOrDefault("DEEPSEEK_FIXTURES", "/raid/haili/dsv4/fixtures"));
        Assumptions.assumeTrue(Files.exists(dir.resolve("model-00003-of-00048.safetensors"))
                && Files.exists(fx.resolve(name + "_prefill.safetensors")), "slice shards or fixtures missing");
        Map<String, Tensor> pre = SafeTensors.read(fx.resolve(name + "_prefill.safetensors").toString(), Device.CPU()).tensors();
        Map<String, Tensor> dec = SafeTensors.read(fx.resolve(name + "_decode.safetensors").toString(), Device.CPU()).tensors();
        Map<Integer, Map<String, Tensor>> rowFiles = new java.util.HashMap<>();
        for (int l : new int[]{1, 14}) {
            Path rp = fx.resolve("engram_rows_layer" + l + ".safetensors");
            if (Files.exists(rp)) {
                rowFiles.put(l, SafeTensors.read(rp.toString(), Device.CPU()).tensors());
            }
        }

        DeepSeekModelArgs args = DeepSeekModelArgs.load(dir.resolve("config.json").toFile()).slice(layers).withMaxSeqLen(maxSeq);
        long[] idv = pre.get("ids").longArray();
        int[] ids = new int[idv.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = (int) idv[i];
        }
        try (DeepSeekModel model = new DeepSeekModel(args, dir, l -> {
            var r = rowFiles.get(l);
            return new DeepSeekEngram.InMemoryRows(r.get("rows"), r.get("w"), r.get("s"));
        })) {
            if (System.getenv("DEEPSEEK_NOISE") != null) {
                model.perturbEmbedding(Double.parseDouble(System.getenv("DEEPSEEK_NOISE")));
            }
            Map<String, Tensor> trace = new LinkedHashMap<>();
            if (System.getenv("DEEPSEEK_PROBE") != null) {
                Dsv.probes = new LinkedHashMap<>();
                Dsv.probeReset();
            }
            Tensor logits = model.forward(ids, 0, trace).to(Device.CPU());
            if (Dsv.probes != null) {
                // write probes for offline CPU/GPU comparison
                Map<String, Tensor> save = new LinkedHashMap<>(Dsv.probes);
                Dsv.probes = null;
                java.nio.file.Files.writeString(Path.of(System.getenv("DEEPSEEK_PROBE") + (gpu ? ".gpu" : ".cpu") + ".names"),
                        String.join("\n", save.keySet()));
                for (var pe : save.entrySet()) {
                    float[] fa = Dsv.host(pe.getValue().to(ScalarType.Float)).floatArray();
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(fa.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    bb.asFloatBuffer().put(fa);
                    java.nio.file.Files.write(Path.of(System.getenv("DEEPSEEK_PROBE") + (gpu ? ".gpu." : ".cpu.") + pe.getKey()), bb.array());
                }
            }
            for (var e : trace.entrySet()) {
                Tensor want = pre.get(e.getKey());
                Tensor got = Aten.one("aten::select.int", e.getValue(), 0L, 0L).to(Device.CPU());
                double r = rel(got, want);
                System.out.printf("prefill %-16s rel err %.5f%n", e.getKey(), r);
                // a 4-layer slice amplifies rounding noise (0.2 % noise on the embeddings alone gives 9-11 % in attn_out of layers 2-3,
                // 20 % in the logits and a different argmax), so on CUDA only the residual stream is checked, against that noise floor
                assertTrue((gpu && !e.getKey().endsWith(".out")) || r < tol, e.getKey() + " rel err " + r);
            }
            // negative control: the comparison must be able to fail
            assertTrue(rel(Aten.one("aten::select.int", trace.get("L0.out"), 0L, 0L).to(Device.CPU()), pre.get("L1.out")) > 0.05,
                    "comparing different layers must differ");
            Tensor wantLogits = Aten.one("aten::unsqueeze", pre.get("logits"), 0L);
            System.out.printf("prefill logits rel err %.5f, argmax got %d want %d%n", rel(logits, wantLogits),
                    Aten.one("aten::argmax", logits).longValue(), Aten.one("aten::argmax", wantLogits).longValue());
            assertTrue(rel(logits, wantLogits) < (gpu ? 0.5 : tol));
            if (!gpu) {
                assertEquals(Aten.one("aten::argmax", wantLogits).longValue(), Aten.one("aten::argmax", logits).longValue());
            }
            for (int i = 0; dec.containsKey("step" + i + ".token"); i++) {
                Tensor tok = dec.get("step" + i + ".token");
                Map<String, Tensor> t = new LinkedHashMap<>();
                Tensor lg = model.forward(new int[]{(int) tok.longArray()[0]}, ids.length + i, t).to(Device.CPU());
                Tensor want = Aten.one("aten::unsqueeze", dec.get("step" + i + ".logits"), 0L);
                System.out.printf("decode %d logits rel err %.5f, argmax got %d want %d%n", i, rel(lg, want),
                        Aten.one("aten::argmax", lg).longValue(), Aten.one("aten::argmax", want).longValue());
                if (!gpu) {
                    assertTrue(rel(lg, want) < tol, "decode " + i);
                    assertEquals(Aten.one("aten::argmax", want).longValue(), Aten.one("aten::argmax", lg).longValue(), "decode " + i);
                }
            }
        }
    }
}
