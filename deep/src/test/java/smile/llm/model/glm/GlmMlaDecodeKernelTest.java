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

import java.util.Arrays;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The graph-safe MLA decode kernels (append, scores, top-k, attend) against ATen/torch references on random caches
 * of three requests of different lengths, one of which completes a pool with this step. Opt-in: {@code GLM_CUDA=true}.
 */
public class GlmMlaDecodeKernelTest {
    private static final Device DEV = Device.CUDA((byte) 0);

    private static Tensor o(String op, Object... a) {
        return Aten.one(op, a);
    }

    private static Tensor randn(long... shape) {
        return o("aten::randn", shape, ScalarType.Float).to(DEV);
    }

    private static double rel(Tensor a, Tensor b) {
        Tensor x = a.to(ScalarType.Float).to(Device.CPU());
        Tensor y = b.to(ScalarType.Float).to(Device.CPU());
        return o("aten::linalg_vector_norm", o("aten::sub.Tensor", x, y), 2.0).doubleValue()
                / Math.max(1e-30, o("aten::linalg_vector_norm", y, 2.0).doubleValue());
    }

    private static Tensor ptrs(Tensor... ts) {
        return Tensor.of(Native.dsDataPtrs(ts), ts.length).to(DEV);
    }

    @Test
    public void testGivenRandomCachesThenKernelsEqualReferences() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Assumptions.assumeTrue(Native.mlaDecodeAvailable(), "no MLA decode kernels in the native library");
        int[] lens = {5, 99, 5002};         // 99 + 1 is a multiple of 4: this step completes a pool
        int b = lens.length;
        int cap = 5100;
        Tensor[] lat = new Tensor[b];
        Tensor[] pool = new Tensor[b];
        Tensor[] pend = new Tensor[b];
        for (int i = 0; i < b; i++) {
            lat[i] = randn(cap, 512).to(ScalarType.BFloat16);
            pool[i] = randn(cap / 4 + 2, 128).to(ScalarType.BFloat16);
            pend[i] = randn(4, 256);
        }
        Tensor latPtrs = ptrs(lat);
        Tensor poolPtrs = ptrs(pool);
        Tensor pendPtrs = ptrs(pend);
        Tensor lensT = Tensor.of(lens, b).to(DEV);
        Tensor latNew = randn(b, 512);
        Tensor keyNew = randn(b, 128);
        Tensor gateNew = randn(b, 128);
        Tensor ape = o("aten::mul.Scalar", randn(4, 128), 0.5);
        Tensor[] oldPend = new Tensor[b];
        for (int i = 0; i < b; i++) {
            oldPend[i] = o("aten::clone", pend[i]);
        }

        // ---- append
        Native.mlaDecodeAppend(latNew, keyNew, gateNew, ape, latPtrs, poolPtrs, pendPtrs, lensT);
        for (int i = 0; i < b; i++) {
            int len = lens[i];
            assertEquals(0.0, o("aten::max", o("aten::abs", o("aten::sub.Tensor", o("aten::select.int", lat[i], 0L, (long) len).to(ScalarType.Float),
                    o("aten::select.int", latNew, 0L, (long) i).to(ScalarType.BFloat16).to(ScalarType.Float)))).doubleValue(), "latent row of request " + i);
            Tensor slot = o("aten::select.int", pend[i], 0L, (long) (len & 3));
            assertTrue(rel(o("aten::narrow", slot, 0L, 0L, 128L), o("aten::select.int", keyNew, 0L, (long) i)) < 1e-7, "ring key");
            assertTrue(rel(o("aten::narrow", slot, 0L, 128L, 128L), o("aten::select.int", gateNew, 0L, (long) i)) < 1e-7, "ring gate");
            if (((len + 1) & 3) == 0) {
                Tensor keys = o("aten::stack", new Tensor[]{o("aten::narrow", pend[i], 1L, 0L, 128L)}, 0L);   // [1,4,128]
                Tensor gates = o("aten::narrow", pend[i], 1L, 128L, 128L);                                     // [4,128]
                Tensor probs = o("aten::softmax.int", o("aten::add.Tensor", gates, ape), 0L);
                Tensor want = o("aten::sum.dim_IntList", o("aten::mul.Tensor", probs, o("aten::narrow", pend[i], 1L, 0L, 128L)), new long[]{0}, false);
                double e = rel(o("aten::select.int", pool[i], 0L, (long) (len >> 2)), want);
                assertTrue(e < 1e-2, "completed pool of request " + i + " rel err " + e);   // bf16 storage
            }
        }

        // ---- scores
        int heads = 32;
        Tensor q = randn(b, heads, 128).to(ScalarType.BFloat16);
        Tensor w = o("aten::mul.Scalar", randn(b, heads), 0.2);
        long maxPools = cap / 4;
        Tensor scores = Native.mlaDecodeScores(q, w, poolPtrs, lensT, maxPools, Math.pow(128, -0.5));
        for (int i = 0; i < b; i++) {
            long pools = (lens[i] + 1) / 4;
            Tensor want = Native.indexerScores(o("aten::narrow", q, 0L, (long) i, 1L), o("aten::narrow", w, 0L, (long) i, 1L),
                    pool[i], 0, pools, lens[i], Math.pow(128, -0.5));
            double e = rel(o("aten::narrow", o("aten::select.int", scores, 0L, (long) i), 0L, 0L, pools), o("aten::reshape", want, new long[]{pools}));
            assertTrue(e < 1e-6, "scores of request " + i + " rel err " + e);
        }

        // ---- top-k
        Tensor idx = Native.mlaDecodeTopk(scores, lensT, 512);
        assertEquals(2051, idx.size(1));
        int[] flat = o("aten::clone", idx).to(Device.CPU()).intArray();
        for (int i = 0; i < b; i++) {
            long pools = (lens[i] + 1) / 4;
            long k = Math.min(512, pools);
            Tensor row = o("aten::narrow", o("aten::select.int", scores, 0L, (long) i), 0L, 0L, pools);
            long[] top = Aten.call("aten::topk", row, k, -1L, true, true)[1].to(Device.CPU()).longArray();
            TreeSet<Integer> want = new TreeSet<>();
            for (long p : top) {
                for (int t = 0; t < 4; t++) {
                    want.add((int) (p * 4 + t));
                }
            }
            int tail = (lens[i] + 1) & 3;
            for (int t = 0; t < tail; t++) {
                want.add(lens[i] + 1 - tail + t);
            }
            TreeSet<Integer> got = new TreeSet<>();
            for (int j = 0; j < 2051; j++) {
                int v = flat[i * 2051 + j];
                if (v >= 0) {
                    assertTrue(got.add(v), "duplicate token " + v);
                }
            }
            assertEquals(want, got, "selected tokens of request " + i + " (pools " + pools + ")");
        }

        // ---- attend
        Tensor qabs = o("aten::mul.Scalar", randn(b, 64, 512), 0.2).to(ScalarType.BFloat16);
        double scale = Math.pow(256, -0.5);
        Tensor ctx = Native.mlaDecodeAttend(qabs, latPtrs, idx, scale);
        for (int i = 0; i < b; i++) {
            long[] ids = o("aten::clone", o("aten::select.int", idx, 0L, (long) i)).to(Device.CPU()).to(ScalarType.Int64).longArray();
            long[] valid = Arrays.stream(ids).filter(v -> v >= 0).toArray();
            Tensor sel = o("aten::index_select", lat[i].to(ScalarType.Float), 0L, Tensor.of(valid, valid.length).to(DEV));   // [n, 512]
            Tensor s = o("aten::mul.Scalar", o("aten::matmul", o("aten::select.int", qabs.to(ScalarType.Float), 0L, (long) i), o("aten::t", sel)), scale);
            Tensor want = o("aten::matmul", o("aten::softmax.int", s, -1L), sel);
            double e = rel(o("aten::select.int", ctx, 0L, (long) i), want);
            assertTrue(e < 1e-4, "attention of request " + i + " rel err " + e);
        }
    }
}
