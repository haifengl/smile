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
 * The fp8 latent cache: quantize/dequantize round trip, the append kernels write the same records as the quantize kernel, and
 * the attention kernel on an fp8 cache stays close to the bf16 cache. Opt-in: {@code GLM_CUDA=true}.
 */
public class GlmLatentFp8Test {
    private static final Device DEV = Device.CUDA((byte) 0);

    private static Tensor o(String op, Object... args) {
        return Aten.one(op, args);
    }

    private static Tensor randn(long... shape) {
        return o("aten::randn", shape, ScalarType.Float).to(DEV);
    }

    private static double rel(Tensor a, Tensor b) {
        return o("aten::linalg_vector_norm", o("aten::sub.Tensor", a.to(ScalarType.Float), b.to(ScalarType.Float)), 2.0).doubleValue()
                / o("aten::linalg_vector_norm", b.to(ScalarType.Float), 2.0).doubleValue();
    }

    private static Tensor ptrs(Tensor... ts) {
        return Tensor.of(Native.dsDataPtrs(ts), ts.length).to(DEV);
    }

    private static Tensor ints(int... v) {
        return Tensor.of(v, v.length).to(DEV);
    }

    @Test
    public void testGivenRowsThenRoundTripAndAppendKernelsAgree() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Assumptions.assumeTrue(Native.glmLatentFp8Available() && Native.windowAvailable(), "no fp8 latent kernels in the native library");
        int n = 300;
        Tensor rows = o("aten::mul.Tensor", randn(n, 512), o("aten::exp", randn(n, 1)));   // rows of different magnitudes
        Tensor cache = o("aten::zeros", new long[]{n, 528}, ScalarType.UInt8).to(DEV);
        Native.glmLatentQuantize(rows, cache);
        long[] idv = new long[n];
        for (int i = 0; i < n; i++) {
            idv[i] = i;
        }
        Tensor back = Native.glmLatentGather(cache, Tensor.of(idv, n).to(DEV)).to(ScalarType.Float);
        // per 128-dim tile: the error relative to the tile maximum
        Tensor tiles = o("aten::reshape", rows, new long[]{n, 4, 128});
        Tensor err = o("aten::abs", o("aten::sub.Tensor", o("aten::reshape", back, new long[]{n, 4, 128}), tiles));
        double worst = o("aten::max", o("aten::div.Tensor", err, o("aten::amax", o("aten::abs", tiles), new long[]{-1}, true))).doubleValue();
        double relL2 = rel(back, rows);
        System.out.printf("fp8 latent round trip: worst error %.4f of the tile maximum, relative L2 %.4f%n", worst, relL2);
        assertTrue(worst < 0.07, "worst tile-relative error " + worst);
        assertTrue(relL2 < 0.04, "relative L2 error " + relL2);

        // the append kernels write the records of the quantize kernel
        Tensor ape = randn(4, 128);
        Tensor lat = o("aten::zeros", new long[]{64, 528}, ScalarType.UInt8).to(DEV);
        Tensor pool = o("aten::zeros", new long[]{16, 128}, ScalarType.BFloat16).to(DEV);
        Tensor ring = o("aten::zeros", new long[]{4, 256}, ScalarType.Float).to(DEV);
        for (int t = 0; t < 9; t++) {
            Native.mlaDecodeAppend(o("aten::narrow", rows, 0L, (long) t, 1L), randn(1, 128), randn(1, 128), ape, ptrs(lat), ptrs(pool),
                    ptrs(ring), ints(t), true);
        }
        assertEquals(0.0, o("aten::max", o("aten::abs", o("aten::sub.Tensor", o("aten::narrow", lat, 0L, 0L, 9L).to(ScalarType.Int32),
                o("aten::narrow", cache, 0L, 0L, 9L).to(ScalarType.Int32)))).doubleValue(), "decode append vs quantize kernel");
        Tensor lat2 = o("aten::zeros", new long[]{64, 528}, ScalarType.UInt8).to(DEV);
        Native.mlaWindowAppend(o("aten::narrow", rows, 0L, 3L, 4L), randn(4, 128), randn(4, 128), ape, ptrs(lat2), ptrs(pool), ptrs(ring),
                ints(0), 4, true);
        assertEquals(0.0, o("aten::max", o("aten::abs", o("aten::sub.Tensor", o("aten::narrow", lat2, 0L, 0L, 4L).to(ScalarType.Int32),
                o("aten::narrow", cache, 0L, 3L, 4L).to(ScalarType.Int32)))).doubleValue(), "window append vs quantize kernel");
    }

    @Test
    public void testGivenCachesThenAttentionOnFp8StaysCloseToBf16() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Assumptions.assumeTrue(Native.glmLatentFp8Available(), "no fp8 latent kernels in the native library");
        int tokens = 3000;
        int heads = 8;
        int topPools = 512;
        Tensor rows = o("aten::mul.Scalar", randn(tokens, 512), 1.5);
        Tensor c8 = o("aten::zeros", new long[]{tokens, 528}, ScalarType.UInt8).to(DEV);
        Native.glmLatentQuantize(rows, c8);
        Tensor c16 = rows.to(ScalarType.BFloat16);
        Tensor q = o("aten::mul.Scalar", randn(2, heads, 512), 0.2).to(ScalarType.BFloat16);
        // two requests with 3000 and 1000 tokens: ids of the selected pools (all of the 750 / 250 pools: > 512 selects 512 of them)
        int width = topPools * 4 + 3;
        int[] ids = new int[2 * width];
        java.util.Arrays.fill(ids, -1);
        java.util.Random rnd = new java.util.Random(5);
        int[] lens = {tokens - 1, 999};
        for (int b = 0; b < 2; b++) {
            int pools = (lens[b] + 1) >> 2;
            int k = Math.min(pools, topPools);
            java.util.List<Integer> all = new java.util.ArrayList<>();
            for (int i = 0; i < pools; i++) {
                all.add(i);
            }
            java.util.Collections.shuffle(all, rnd);
            for (int j = 0; j < k; j++) {
                for (int t = 0; t < 4; t++) {
                    ids[b * width + j * 4 + t] = all.get(j) * 4 + t;
                }
            }
            int tail = (lens[b] + 1) & 3;
            for (int t = 0; t < 3; t++) {
                ids[b * width + width - 3 + t] = t < tail ? lens[b] + 1 - tail + t : -1;
            }
        }
        Tensor idx = Tensor.of(ids, 2, width).to(DEV);
        Tensor lensT = ints(lens);
        Tensor ctx16 = Native.mlaDecodeAttend(q, ptrs(c16, c16), idx, lensT, topPools, 0.0625, false);
        Tensor ctx8 = Native.mlaDecodeAttend(q, ptrs(c8, c8), idx, lensT, topPools, 0.0625, true);
        double e = rel(ctx8, ctx16);
        Tensor full8 = Native.mlaDecodeAttend(q, ptrs(c8, c8), idx, null, 0, 0.0625, true);
        System.out.printf("attention on an fp8 cache vs bf16: relative L2 %.4f (null lens path vs lens path: %.2e)%n", e, rel(full8, ctx8));
        assertTrue(e < 0.03, "fp8 attention error " + e);
        assertTrue(rel(full8, ctx8) < 1e-6, "the full-width path must agree with the bounded one");
    }
}
