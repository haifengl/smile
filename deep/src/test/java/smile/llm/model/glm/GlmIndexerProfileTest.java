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

/** Splits the indexer's long-prefill cost into scoring and top-k (no assertions). Opt-in: GLM_CUDA=true GLM_BENCH=1. */
public class GlmIndexerProfileTest {
    private static Tensor o(String op, Object... a) {
        return Aten.one(op, a);
    }

    private static Tensor randn(long... shape) {
        return o("aten::randn", shape, ScalarType.Float).to(Device.CUDA((byte) 0));
    }

    private static void sync(Tensor t) {
        o("aten::sum", t.to(ScalarType.Float)).doubleValue();
    }

    @Test
    public void testProfile() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")) && System.getenv("GLM_BENCH") != null, "set GLM_BENCH=1");
        Gx.setDevice(Device.CUDA((byte) 0));
        long n = 512;
        long pools = 262144;
        long tile = 8192;
        Tensor pk = randn(pools, 128).to(ScalarType.BFloat16);
        Tensor qi = randn(n * 32, 128).to(ScalarType.BFloat16);
        Tensor w = randn(n, 1, 32);
        Tensor tileIdx = randn(n, tile);
        for (int rep = 0; rep < 2; rep++) {
            sync(pk);
            long t0 = System.nanoTime();
            Tensor last = null;
            for (long t = 0; t < pools; t += tile) {
                last = Gx.scopedAll(() -> {
                    Tensor s = o("aten::mm.dtype", qi, o("aten::t", o("aten::narrow", pk, 0L, 0L, tile)), ScalarType.Float);
                    s = o("aten::relu", o("aten::mul.Scalar", s, 0.088));
                    s = o("aten::reshape", s, new long[]{n, 32, tile});
                    return new Tensor[]{o("aten::reshape", o("aten::bmm", w, s), new long[]{n, tile})};
                })[0];
            }
            sync(last);
            double score = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            for (long t = 0; t < pools; t += tile) {
                last = Gx.scopedAll(() -> new Tensor[]{Aten.call("aten::topk", tileIdx, 512L, -1L, true, true)[1]})[0];
            }
            sync(last);
            double topk = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            for (long t = 0; t < pools; t += tile) {
                last = Gx.scopedAll(() -> new Tensor[]{o("aten::masked_fill.Scalar", tileIdx, o("aten::lt.Scalar", tileIdx, 0.0), -1e30)})[0];
            }
            sync(last);
            double mask = (System.nanoTime() - t0) / 1e6;
            Tensor qk = randn(n, 32, 128).to(ScalarType.BFloat16);
            Tensor wk = randn(n, 32);
            sync(pk);
            t0 = System.nanoTime();
            for (long t = 0; t < pools; t += tile * 2) {
                last = smile.torch.Native.indexerScores(qk, wk, pk, t, tile * 2, 524288, 0.088);
                sync(last);
            }
            double fusedMs = (System.nanoTime() - t0) / 1e6;
            System.out.printf("fused scoring kernel over all pools (tiles of 16384, with a sync each): %.1f ms%n", fusedMs);
            System.out.printf("n=%d pools=%d: scoring (mm+relu+bmm) %.1f ms, topk(512) over 32 tiles of 8192 %.1f ms, one masked_fill pass %.1f ms%n",
                    n, pools, score, topk, mask);
        }
    }
}
