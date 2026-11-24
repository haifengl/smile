/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import java.util.List;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.InklingNative;

/**
 * The row-wise fused kernels of the decode path ({@code smile_inkling_ops.cu}) applied to the rows of a prefill chunk: a row
 * of a chunk is independent of the others in these operations, so one launch replaces the dozens of elementwise ATen
 * operations the reference path runs on {@code [T, D]} float32 tensors (which are bound by memory traffic).
 *
 * @author Haifeng Li
 */
final class InklingKernels {
    /** Whether prefill uses the fused row kernels ({@code SMILE_INKLING_FUSED_PREFILL=0} switches them off). */
    static final boolean ENABLED = !"0".equals(System.getenv("SMILE_INKLING_FUSED_PREFILL")) && InklingNative.isAvailable();
    /** Prefill chunks use the fused decoder (tests switch it to compare with the reference path). */
    static volatile boolean prefill = ENABLED;

    private InklingKernels() { }

    private static long p(Tensor t) {
        return InklingNative.dataPtr(t);
    }

    private static Tensor empty(Device device, ScalarType type, long... shape) {
        return Tensor.empty(new Tensor.Options().dtype(type).device(device), shape);
    }

    /**
     * AttnRes read of the states followed by the weighted RMSNorm of each query's result, in one launch (at most 9 states).
     *
     * @param nb     rows.
     * @param states completed blocks and the current state, bf16 or float32 {@code [nb, dim]} each.
     * @param query  float32 {@code [nq, dim]}.
     * @param w      per-query norm weights, float32 {@code [dim]}.
     * @param want32 also produce float32 copies of the rounded outputs (into {@code out32}).
     * @return the bf16 outputs {@code [nb, dim]}, one per query.
     */
    static Tensor[] attnResRms(Device device, int nb, int dim, List<Tensor> states, Tensor query, Tensor[] w, boolean want32,
                               Tensor[] out32) {
        int nq = w.length;
        int n = states.size();
        Tensor[] y = new Tensor[nq];
        long[] ptrs = new long[2 + 3 * nq + n];
        ptrs[0] = 0;
        ptrs[1] = p(query);
        long mask = 0;
        for (int j = 0; j < nq; j++) {
            y[j] = empty(device, ScalarType.BFloat16, nb, dim);
            ptrs[2 + j] = p(w[j]);
            ptrs[2 + nq + j] = p(y[j]);
            if (want32) {
                out32[j] = empty(device, ScalarType.Float, nb, dim);
                ptrs[2 + 2 * nq + j] = p(out32[j]);
            }
        }
        for (int i = 0; i < n; i++) {
            Tensor s = states.get(i);
            ptrs[2 + 3 * nq + i] = p(s);
            if (s.dtype() == ScalarType.Float) {
                mask |= 1L << i;
            }
        }
        InklingNative.op(14, ptrs, new long[]{nb, nq, dim, n, mask, 1}, new double[0]);
        return y;
    }
}
