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
package smile.llm.model.glm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The first layers of GLM-5.3-Flash (three KDA layers with dense MLPs, then an MLA + indexer layer with the MoE)
 * against the traces of transformers' {@code Glm5NextTextModel} run in float32 ({@code glm_ref.py --dtype float32}).
 *
 * <p>Opt-in: needs the slice shards in {@code GLM_DIR} (default {@code /raid/haili/glm/raw}), the fixtures in
 * {@code GLM_FIXTURES} (default {@code /raid/haili/glm/fixtures}) and the native library. Both sides compute in
 * float32 (the weights are bf16 or fp8 values, exact in float32), so the comparison is tight; the residual
 * differences are summation orders.
 */
public class GlmSliceTest {
    private static final double TOL = Double.parseDouble(System.getenv().getOrDefault("GLM_TOL", "2e-4"));

    private static Path dir() {
        return Path.of(System.getenv().getOrDefault("GLM_DIR", "/raid/haili/glm/raw"));
    }

    private static Path fixtures() {
        return Path.of(System.getenv().getOrDefault("GLM_FIXTURES", "/raid/haili/glm/fixtures"));
    }

    private static Tensor f32(Tensor t) {
        return t.to(smile.deep.tensor.ScalarType.Float);
    }

    private static double rel(Tensor a, Tensor b) {
        assertEquals(Arrays.stream(b.shape()).reduce(1L, (x, y) -> x * y), Arrays.stream(a.shape()).reduce(1L, (x, y) -> x * y), "element count of " + Arrays.toString(a.shape()) + " vs " + Arrays.toString(b.shape()));
        Tensor bb = f32(b).reshape(a.shape());
        Tensor d = Aten.one("aten::sub.Tensor", f32(a).to(Device.CPU()), bb);
        double num = Aten.one("aten::linalg_vector_norm", d, 2.0).doubleValue();
        double den = Aten.one("aten::linalg_vector_norm", bb, 2.0).doubleValue();
        return num / Math.max(den, 1e-30);
    }

    /** Drops the leading batch dimension of a fixture tensor. */
    private static Tensor drop0(Tensor t) {
        return Aten.one("aten::reshape", t, Arrays.copyOfRange(t.shape(), 1, t.shape().length));
    }

    /** The last {@code rows} rows (dim 0) of a host tensor. */
    private static Tensor tail(Tensor t, long rows) {
        return Aten.one("aten::narrow", t, 0L, t.size(0) - rows, rows);
    }

    private static int[] ints(JsonNode a) {
        int[] r = new int[a.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = a.get(i).asInt();
        }
        return r;
    }

    /** Compares the selected token sets of every query row; returns the number of rows whose sets differ. */
    private static int topkRowMismatches(Tensor got, Tensor want) {
        long rows = got.size(0);
        long[] g = Aten.one("aten::clone", Aten.one("aten::reshape", got.to(smile.deep.tensor.ScalarType.Int64), new long[]{-1L})).longArray();
        long[] w = Aten.one("aten::clone", Aten.one("aten::reshape", want.to(smile.deep.tensor.ScalarType.Int64), new long[]{-1L})).longArray();
        int gw = (int) (g.length / rows);
        int ww = (int) (w.length / rows);
        int bad = 0;
        for (int r = 0; r < rows; r++) {
            TreeSet<Long> a = new TreeSet<>();
            TreeSet<Long> b = new TreeSet<>();
            for (int i = 0; i < gw; i++) {
                if (g[r * gw + i] >= 0) {
                    a.add(g[r * gw + i]);
                }
            }
            for (int i = 0; i < ww; i++) {
                if (w[r * ww + i] >= 0) {
                    b.add(w[r * ww + i]);
                }
            }
            if (!a.equals(b)) {
                bad++;
            }
        }
        return bad;
    }

    private static void compare(String step, Map<String, Tensor> got, Map<String, Tensor> want, int layers, long rows,
                                StringBuilder report) {
        for (int i = 0; i < layers; i++) {
            for (String k : new String[]{"attn_hc.post", "attn_hc.comb", "attn_hc.collapsed", "attn_in", "attn", "ffn", "out"}) {
                String name = "L" + i + "." + k;
                Tensor w = want.get(step + "." + name);
                if (w == null) {
                    continue;   // KDA-only or MLA-only entries
                }
                double e = rel(tail(got.get(name), rows), tail(drop0(w), rows));
                report.append(String.format("%s %-22s rel %.2e%n", step, name, e));
                assertTrue(e < TOL, step + " " + name + " rel err " + e);
            }
            Tensor wt = want.get(step + ".L" + i + ".topk");
            if (wt != null) {
                int bad = topkRowMismatches(tail(got.get("L" + i + ".topk"), rows), tail(drop0(wt), rows));
                report.append(String.format("%s L%d.topk rows with a different selection: %d of %d%n", step, i, bad, rows));
                assertTrue(bad <= Math.max(1, rows / 50), step + " topk mismatching rows " + bad);
            }
        }
    }

    private void run(String fixture, String part, int layers, int chunk) throws Exception {
        Path f = fixtures().resolve(fixture + "_" + part + ".safetensors");
        Assumptions.assumeTrue(Files.exists(f) && Files.exists(dir().resolve("model.safetensors.index.json")),
                "no fixtures at " + f);
        Map<String, Tensor> want = SafeTensors.read(f.toString(), Device.CPU()).tensors();
        JsonNode meta = new ObjectMapper().readTree(fixtures().resolve(fixture + "_meta.json").toFile()).get(part);
        int[] ids = ints(meta.get("ids"));
        int[] generated = ints(meta.get("generated"));
        GlmModelArgs args = GlmModelArgs.load(new File(dir().toFile(), "config.json")).slice(layers);
        StringBuilder report = new StringBuilder();
        try (GlmModel model = new GlmModel(dir(), args); GlmModel.Session session = model.newSession()) {
            Map<String, Tensor> trace = new HashMap<>();
            Tensor hidden = null;
            for (int from = 0; from < ids.length; from += chunk) {
                trace.clear();
                hidden = model.forward(Arrays.copyOfRange(ids, from, Math.min(ids.length, from + chunk)), session, trace);
            }
            long keep = want.get("p.hidden").size(1);
            compare("p", trace, want, layers, keep, report);
            double h = rel(tail(hidden, keep), drop0(want.get("p.hidden")));
            double l = rel(model.logits(tail(hidden, keep)), drop0(want.get("p.logits")));
            report.append(String.format("p hidden %.2e logits %.2e%n", h, l));
            assertTrue(h < TOL && l < TOL, "hidden " + h + " logits " + l);
            for (int step = 1; step < generated.length; step++) {
                trace.clear();
                Tensor hd = model.forward(new int[]{generated[step - 1]}, session, trace);
                compare("d" + step, trace, want, layers, 1, report);
                double dh = rel(hd, drop0(want.get("d" + step + ".hidden")));
                double dl = rel(model.logits(hd), drop0(want.get("d" + step + ".logits")));
                report.append(String.format("d%d hidden %.2e logits %.2e argmax %d (oracle %d)%n", step, dh, dl,
                        Aten.one("aten::argmax", model.logits(hd)).longValue(), generated[step]));
                assertTrue(dh < TOL && dl < TOL, "decode " + step + " hidden " + dh + " logits " + dl);
            }
        } finally {
            System.out.println(report);
        }
    }

    @Test
    public void testGivenShortPromptThenLayerTracesAndLogitsMatchOracle() throws Exception {
        run("slice4f", "short", 4, 1 << 20);
    }

    @Test
    public void testGivenLongPromptInChunksThenCarriedStateAndIndexerMatchOracle() throws Exception {
        Assumptions.assumeTrue(System.getenv("GLM_LONG") != null, "set GLM_LONG=1 (minutes on CPU)");
        run("slice4f", "long", 4, 512);
    }

    /**
     * bf16 weights on a GPU: the error against the float32 oracle must be of the order of the error of the bf16
     * oracle (transformers in bfloat16) against the same float32 oracle, i.e. plain bf16 noise.
     */
    @Test
    public void testGivenCudaThenErrorIsBf16Noise() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        int layers = 4;
        String part = System.getenv().getOrDefault("GLM_PART", "short");
        Path f32f = fixtures().resolve("slice4f_" + part + ".safetensors");
        Path bf16f = fixtures().resolve("slice4_" + part + ".safetensors");
        Assumptions.assumeTrue(Files.exists(f32f) && Files.exists(bf16f), "no fixtures");
        Map<String, Tensor> ref = SafeTensors.read(f32f.toString(), Device.CPU()).tensors();
        Map<String, Tensor> bf = SafeTensors.read(bf16f.toString(), Device.CPU()).tensors();
        JsonNode meta = new ObjectMapper().readTree(fixtures().resolve("slice4f_meta.json").toFile()).get(part);
        int[] ids = ints(meta.get("ids"));
        int[] generated = ints(meta.get("generated"));
        int chunk = part.equals("long") ? 512 : 1 << 20;
        GlmModelArgs args = GlmModelArgs.load(new File(dir().toFile(), "config.json")).slice(layers);
        StringBuilder report = new StringBuilder();
        try (GlmModel model = new GlmModel(dir(), args, Device.CUDA((byte) 0)); GlmModel.Session session = model.newSession()) {
            Map<String, Tensor> trace = new HashMap<>();
            Tensor hidden = null;
            for (int from = 0; from < ids.length; from += chunk) {
                trace.clear();
                hidden = model.forward(Arrays.copyOfRange(ids, from, Math.min(ids.length, from + chunk)), session, trace);
            }
            long keep = ref.get("p.hidden").size(1);
            long rows = Math.min(keep, 8);
            for (int i = 0; i < layers; i++) {
                for (String k : new String[]{"attn", "ffn", "out"}) {
                    String name = "L" + i + "." + k;
                    if (!ref.containsKey("p." + name)) {
                        continue;   // the long fixture keeps only the layer outputs
                    }
                    double ej = rel(tail(trace.get(name), rows), tail(drop0(ref.get("p." + name)), rows));
                    double eb = rel(tail(drop0(bf.get("p." + name)), rows), tail(drop0(ref.get("p." + name)), rows));
                    report.append(String.format("p %-10s java-gpu %.2e   bf16-oracle %.2e%n", name, ej, eb));
                    assertTrue(ej <= 4 * eb + 2e-3, name + " java " + ej + " bf16 oracle " + eb);
                }
            }
            double ej = rel(tail(hidden, keep), drop0(ref.get("p.hidden")));
            double eb = rel(drop0(bf.get("p.hidden")), drop0(ref.get("p.hidden")));
            report.append(String.format("p hidden     java-gpu %.2e   bf16-oracle %.2e%n", ej, eb));
            assertTrue(ej <= 4 * eb + 2e-3, "hidden " + ej + " vs " + eb);
            Tensor logits = model.logits(tail(hidden, keep));
            Tensor oracleLast = tail(drop0(ref.get("p.logits")), 1);
            report.append(String.format("p logits(last row) java-gpu %.2e bf16-oracle %.2e argmax %d (fp32 oracle %d)%n",
                    rel(tail(logits, 1), oracleLast), rel(tail(drop0(bf.get("p.logits")), 1), oracleLast),
                    Aten.one("aten::argmax", tail(logits, 1)).longValue(), Aten.one("aten::argmax", oracleLast).longValue()));
            for (int step = 1; step < generated.length; step++) {
                // decode the way the engine does: the batched step (on a GPU: the graph-safe MLA kernels)
                Tensor hd = model.forwardBatch(new int[]{generated[step - 1]}, new GlmModel.Session[]{session});
                double dj = rel(hd, drop0(ref.get("d" + step + ".hidden")));
                double db = rel(drop0(bf.get("d" + step + ".hidden")), drop0(ref.get("d" + step + ".hidden")));
                report.append(String.format("d%d hidden     java-gpu %.2e   bf16-oracle %.2e%n", step, dj, db));
                assertTrue(dj <= 4 * db + 2e-3, "decode " + step + " hidden " + dj + " vs " + db);
            }
        } finally {
            System.out.println(report);
        }
    }
}
