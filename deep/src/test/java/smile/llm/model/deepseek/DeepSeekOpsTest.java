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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks {@link DeepSeekOps} against op-level fixtures produced by the pure-PyTorch port of the reference model
 * ({@code /raid/haili/dsv4/ref/dump_ops_fixture.py}). Opt-in: needs {@code ops.safetensors} in
 * {@code DEEPSEEK_FIXTURES} (default {@code /raid/haili/dsv4/fixtures}) and the native library.
 */
public class DeepSeekOpsTest {
    private static Map<String, Tensor> f;

    @BeforeAll
    static void load() throws Exception {
        Path p = Path.of(System.getenv().getOrDefault("DEEPSEEK_FIXTURES", "/raid/haili/dsv4/fixtures"), "ops.safetensors");
        Assumptions.assumeTrue(Files.exists(p), "no fixtures at " + p);
        f = SafeTensors.read(p.toString(), Device.CPU()).tensors();
    }

    /** Largest absolute difference after casting both to float32. */
    static double maxDiff(Tensor a, Tensor b) {
        assertArrayEquals(a.shape(), b.shape(), "shape");
        Tensor d = Aten.one("aten::abs", Aten.one("aten::sub.Tensor", DeepSeekOps.f32(a), DeepSeekOps.f32(b)));
        return Aten.one("aten::max", d).doubleValue();
    }

    private static void exact(String got, Tensor a, String want) {
        assertEquals(0.0, maxDiff(a, f.get(want)), got + " must equal " + want + " exactly");
    }

    @Test
    public void testFakeQuantFp8MatchesReference() {
        exact("fp8", DeepSeekOps.fakeQuantFp8(f.get("fq.x"), 32), "fq.fp8");
    }

    @Test
    public void testFakeQuantFp4MatchesReference() {
        exact("fp4 e8m0", DeepSeekOps.fakeQuantFp4(f.get("fq.x"), 32, false), "fq.fp4_e8m0");
        exact("fp4 e4m3", DeepSeekOps.fakeQuantFp4(f.get("fq.x"), 16, true), "fq.fp4_e4m3_16");
    }

    @Test
    public void testRoundFp4TiesToEven() {
        Tensor v = Aten.one("aten::clamp", f.get("fp4.v"), -6.0, 6.0);
        exact("round fp4", DeepSeekOps.roundFp4(v), "fp4.r");
    }

    @Test
    public void testPow2Ceil() {
        exact("pow2", DeepSeekOps.pow2Ceil(f.get("p2.v")), "p2.r");
    }

    @Test
    public void testHyperConnectionSinkhorn() {
        Tensor[] r = DeepSeekOps.hcSplitSinkhorn(f.get("hc.mixes"), f.get("hc.scale"), f.get("hc.base"), 4, 20, 1e-6);
        assertTrue(maxDiff(r[0], f.get("hc.pre")) < 1e-6, "pre");
        assertTrue(maxDiff(r[1], f.get("hc.post")) < 1e-6, "post");
        assertTrue(maxDiff(r[2], f.get("hc.comb")) < 1e-6, "comb " + maxDiff(r[2], f.get("hc.comb")));
        // doubly stochastic up to the final column normalization
        Tensor colSums = Aten.one("aten::sum.dim_IntList", r[2], new long[]{-2});
        assertTrue(maxDiff(colSums, Aten.one("aten::ones_like", colSums)) < 1e-4);
    }

    @Test
    public void testRopeTablesAndApplication() {
        Tensor plain = DeepSeekOps.precomputeFreqsCis(64, 300, 0, 10000, 16, 32, 1);
        Tensor yarn = DeepSeekOps.precomputeFreqsCis(64, 300, 65536, 160000, 16, 32, 1);
        assertTrue(maxDiff(Aten.one("aten::view_as_real", plain), f.get("rope.plain")) < 1e-4, "plain");
        assertTrue(maxDiff(Aten.one("aten::view_as_real", yarn), f.get("rope.yarn")) < 1e-4,
                "yarn " + maxDiff(Aten.one("aten::view_as_real", yarn), f.get("rope.yarn")));
        Tensor part = Aten.one("aten::narrow", yarn, 0L, 3L, 7L);
        assertTrue(maxDiff(DeepSeekOps.rope(f.get("rope.x"), part, false), f.get("rope.y")) < 2e-2, "rope");
        assertTrue(maxDiff(DeepSeekOps.rope(f.get("rope.x"), part, true), f.get("rope.yi")) < 2e-2, "inverse rope");
    }

    @Test
    public void testRmsNormWithTinyEpsilon() {
        double d = maxDiff(DeepSeekOps.rmsNorm(f.get("rms.x"), f.get("rms.w"), 1e-20), f.get("rms.y"));
        assertTrue(d < 5e-2, "rms " + d);
    }

    @Test
    public void testSparseAttentionWithSinkAndInvalidRows() {
        Tensor o = DeepSeekOps.sparseAttention(f.get("sa.q"), f.get("sa.kv"), f.get("sa.sink"), f.get("sa.idx"), Math.pow(64, -0.5));
        double d = maxDiff(o, f.get("sa.o"));
        assertTrue(d < 2e-2, "sparse attention " + d);
        // the all-invalid query row has no key: its output is zero
        Tensor last = Aten.one("aten::select.int", Aten.one("aten::select.int", o, 0L, 0L), 0L, 2L);
        assertEquals(0.0, Aten.one("aten::max", Aten.one("aten::abs", DeepSeekOps.f32(last))).doubleValue());
    }

    @Test
    public void testWindowIndices() {
        Tensor pre = Aten.one("aten::select.int", DeepSeekOps.windowTopkIdxs(8, 12, 0), 0L, 0L);
        assertEquals(0.0, maxDiff(pre.to(ScalarType.Int32), f.get("win.prefill")));
        Tensor dec = Aten.one("aten::select.int", DeepSeekOps.windowTopkIdxs(8, 1, 19), 0L, 0L);   // [1, width]
        assertEquals(0.0, maxDiff(dec.to(ScalarType.Int32), f.get("win.decode")));
    }

    @Test
    public void testWeightDequantization() {
        exact("fp8 weight", DeepSeekOps.dequantFp8(Aten.one("aten::view.dtype", f.get("dq8.w"), ScalarType.Float8e4m3fn), f.get("dq8.s")), "dq8.y");
        exact("fp4 weight", DeepSeekOps.dequantFp4(f.get("dq4.w"), f.get("dq4.s")), "dq4.y");
    }
}
