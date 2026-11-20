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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fused indexer scoring kernel against the torch-op sequence it replaces (matmul, relu, head-weighted sum, causal
 * mask) on random data with awkward sizes. Opt-in: {@code GLM_CUDA=true}.
 */
public class GlmIndexerKernelTest {
    private static Tensor o(String op, Object... a) {
        return Aten.one(op, a);
    }

    private static Tensor randn(long... shape) {
        return o("aten::randn", shape, ScalarType.Float).to(Device.CUDA((byte) 0));
    }

    private static Tensor reference(Tensor q, Tensor w, Tensor pk, long pool0, long count, long p0, double scale) {
        long n = q.size(0);
        Tensor range = o("aten::narrow", pk, 0L, pool0, count);
        Tensor s = o("aten::mm.dtype", o("aten::reshape", q, new long[]{n * 32, 128}), o("aten::t", range), ScalarType.Float);
        s = o("aten::reshape", o("aten::relu", o("aten::mul.Scalar", s, scale)), new long[]{n, 32, count});
        Tensor index = o("aten::reshape", o("aten::bmm", o("aten::unsqueeze", w, 1L), s), new long[]{n, count});
        Tensor poolEnd = o("aten::unsqueeze", o("aten::add.Scalar", o("aten::mul.Scalar",
                o("aten::arange.start", pool0, pool0 + count, ScalarType.Int64).to(Device.CUDA((byte) 0)), 4L), 3L), 0L);
        Tensor qpos = o("aten::unsqueeze", o("aten::arange.start", p0, p0 + n, ScalarType.Int64).to(Device.CUDA((byte) 0)), 1L);
        Tensor visible = o("aten::le.Tensor", poolEnd, qpos);
        return o("aten::masked_fill.Scalar", index, o("aten::logical_not", visible), -3.4028234663852886e38);
    }

    @Test
    public void testGivenRandomDataThenKernelEqualsTorchOps() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Assumptions.assumeTrue(Native.indexerScoresAvailable(), "no indexer kernel in the native library");
        long[][] cases = {   // n, P, pool0, count, p0
                {37, 1000, 17, 700, 2500}, {1, 5000, 0, 5000, 20000}, {9, 129, 3, 100, 300}, {512, 4096, 0, 4096, 8192},
                {8, 64, 0, 64, 0}, {3, 70000, 1000, 65000, 150000}};
        for (long[] c : cases) {
            long n = c[0], p = c[1], pool0 = c[2], count = c[3], p0 = c[4];
            Tensor q = randn(n, 32, 128).to(ScalarType.BFloat16);
            Tensor w = o("aten::mul.Scalar", randn(n, 32), 0.2);
            Tensor pk = randn(p, 128).to(ScalarType.BFloat16);
            double scale = Math.pow(128, -0.5);
            Tensor got = Native.indexerScores(q, w, pk, pool0, count, p0, scale);
            Tensor want = reference(q, w, pk, pool0, count, p0, scale);
            assertArrayEquals(want.shape(), got.shape());
            Tensor visible = o("aten::gt.Scalar", want, -1e30);
            // invisible entries are the float32 lowest in both
            double bad = o("aten::sum", o("aten::ne.Tensor", o("aten::gt.Scalar", got, -1e30), visible).to(ScalarType.Float)).doubleValue();
            assertEquals(0.0, bad, "visibility mask differs for case " + java.util.Arrays.toString(c));
            Tensor diff = o("aten::where.ScalarOther", visible, o("aten::sub.Tensor", got, want), 0.0);
            double num = o("aten::linalg_vector_norm", diff, 2.0).doubleValue();
            double den = o("aten::linalg_vector_norm", o("aten::where.ScalarOther", visible, want, 0.0), 2.0).doubleValue();
            double e = den == 0 ? num : num / den;
            System.out.printf("indexer kernel n=%d count=%d: rel err %.2e (visible fraction %.2f)%n", n, count, e,
                    o("aten::mean", visible.to(ScalarType.Float)).doubleValue());
            assertTrue(e < 2e-5, "case " + java.util.Arrays.toString(c) + " rel err " + e);
        }
    }
}
