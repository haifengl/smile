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
package smile.llm;

import java.util.Arrays;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.util.AutoScope;
import smile.util.Tuple2;

/**
 * Rotary positional encoding (RoPE). RoPE encodes the absolute position with
 * a rotation matrix and meanwhile incorporates the explicit relative position
 * dependency in self-attention formulation. Notably, RoPE enables the
 * flexibility of sequence length, decaying inter-token dependency with
 * increasing relative distances, and the capability of equipping the linear
 * self-attention with relative position encoding.
 *
 * @author Haifeng Li
 */
public interface RotaryPositionalEncoding {
    /**
     * Applies rotary embeddings to the input query and key tensors.
     * It ensures that the output tensors have the same data type as
     * the input tensors.
     *
     * @param xq the query tensor.
     * @param xk the key tensor.
     * @param cis the frequency tensor for complex exponentials.
     * @return the tuple of modified query tensor and key tensor with
     * rotary embeddings.
     */
    static Tuple2<Tensor, Tensor> apply(Tensor xq, Tensor xk, Tensor cis) {
        int ndim = xq.dim();
        long[] xqShape = Arrays.copyOf(xq.shape(), ndim + 1);
        long[] xkShape = Arrays.copyOf(xk.shape(), ndim + 1);
        xqShape[ndim - 1] = xkShape[ndim - 1] = -1;
        xqShape[ndim] = xkShape[ndim] = 2;

        try (var scope = new AutoScope()) {
            Tensor xq_ = scope.add(xq.to(ScalarType.Float32).reshape(xqShape).viewAsComplex());
            Tensor xk_ = scope.add(xk.to(ScalarType.Float32).reshape(xkShape).viewAsComplex());
            Tensor pe = scope.add(reshapeForBroadcast(cis, xq_));
            Tensor xq_out = scope.add(xq_.mul_(pe).viewAsReal().flatten(3));
            Tensor xk_out = scope.add(xk_.mul_(pe).viewAsReal().flatten(3));
            return new Tuple2<>(xq_out.to(xq.dtype()), xk_out.to(xk.dtype()));
        }
    }

    /**
     * Precompute the frequency tensor for complex exponentials (cis).
     * with default theta 10000.0.
     * @param dim the dimension of the frequency tensor.
     * @param end the end index for precomputing frequencies.
     * @return the precomputed frequency tensor for complex exponentials.
     */
    static Tensor computeFreqCis(int dim, int end) {
        return  computeFreqCis(dim, end, 10000.0, false);
    }

    /**
     * Precompute the frequency tensor for complex exponentials (cis).
     * @param dim the dimension of the frequency tensor.
     * @param end the end index for precomputing frequencies.
     * @param theta the scaling factor for frequency computation.
     * @param scaling if true, scale the frequency tensor.
     * @return the precomputed frequency tensor for complex exponentials.
     */
    static Tensor computeFreqCis(int dim, int end, double theta, boolean scaling) {
        // Explicitly convert tensor to float32 as the default is bf16.
        // On the other hand, view_as_complex cannot apply on bf16.
        try (Tensor t = Tensor.arange(0, end, 1).to(ScalarType.Float32);
             Tensor f = Tensor.arange(0, dim, 2).to(ScalarType.Float32).mul_(-Math.log(theta) / dim).exp_();
             Tensor freqs = scaling ?  scale(f) : f;
             Tensor tfreqs = t.outer(freqs)) {
            return Tensor.polar(freqs.newOnes(), tfreqs); // complex64
        }
    }

    /**
     * Reshapes the cis tensor to match the shape of the target tensor x for
     * broadcasting purposes, allowing for element-wise operations between
     * tensors of compatible shapes.
     * @param cis the frequency tensor for complex exponentials.
     * @param x the target tensor for broadcasting.
     * @return the reshaped cis tensor view.
     */
    static Tensor reshapeForBroadcast(Tensor cis, Tensor x) {
        int dim = x.dim();
        long[] xs = x.shape();
        long[] shape = new long[dim];
        Arrays.fill(shape, 1);
        shape[1] = xs[1];
        shape[dim-1] = xs[dim-1];
        return cis.view(shape);
    }

    /**
     * Adapts RoPE to longer input lengths.
     * @param freqs the frequency tensor.
     * @return the scaled frequency tensor.
     */
    static Tensor scale(Tensor freqs) {
        // Values obtained from grid search
        int scale_factor = 8;
        int low_freq_factor = 1;
        int high_freq_factor = 4;
        int old_context_len = 8192;  // original llama3 length

        int low_freq_wavelen = old_context_len / low_freq_factor;
        int high_freq_wavelen = old_context_len / high_freq_factor;
        int n = (int) freqs.shape()[0];
        for (int i = 0; i < n; i++) {
            float freq = freqs.getFloat(i);
            float wavelen = (float) (2 * Math.PI / freq);
            if (wavelen < high_freq_wavelen) {
                // freqs.put_(freq, i);
            } else if (wavelen > low_freq_wavelen) {
                freqs.put_(freq / scale_factor, i);
            } else {
                // assert low_freq_wavelen != high_freq_wavelen;
                float smooth = (old_context_len / wavelen - low_freq_factor) / (high_freq_factor - low_freq_factor);
                freqs.put_((1 - smooth) * freq / scale_factor + smooth * freq, i);
            }
        }
        return freqs;
    }
}
