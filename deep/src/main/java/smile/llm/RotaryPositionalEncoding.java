/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.llm;

import java.util.Arrays;

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
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
     * @return the tuple of modified query tensor and key tensor with
     * rotary embeddings.
     */
    static Tuple2<Tensor, Tensor> apply(Tensor xq, Tensor xk, Tensor cis) {
        int ndim = xq.dim();
        long[] xqShape = Arrays.copyOf(xq.shape(), ndim + 1);
        long[] xkShape = Arrays.copyOf(xk.shape(), ndim + 1);
        xqShape[ndim - 1] = xkShape[ndim - 1] = -1;
        xqShape[ndim] = xkShape[ndim] = 2;

        try (Tensor xq_ = xq.reshape(xqShape).viewAsComplex();
             Tensor xk_ = xk.reshape(xkShape).viewAsComplex();
             Tensor pe = reshapeForBroadcast(cis, xq_)) {
            Tensor xq_out = xq_.mul_(pe).viewAsReal().flatten(3);
            Tensor xk_out = xk_.mul_(pe).viewAsReal().flatten(3);
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
        return  computeFreqCis(dim, end, 10000.0);
    }

    /**
     * Precompute the frequency tensor for complex exponentials (cis).
     * @param dim the dimension of the frequency tensor.
     * @param end the end index for precomputing frequencies.
     * @param theta the scaling factor for frequency computation.
     * @return the precomputed frequency tensor for complex exponentials.
     */
    static Tensor computeFreqCis(int dim, int end, double theta) {
        try (Tensor t = Tensor.arange(0, end,1).to(ScalarType.Float32);
             Tensor freqs = Tensor.arange(0, dim, 2).to(ScalarType.Float32).mul_(-Math.log(theta) / dim).exp_();
             Tensor tfreqs = t.outer(freqs)) {
            var cis = Tensor.ones(tfreqs.shape()).polar(tfreqs);  // complex64
            return cis;
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
        int ndim = x.dim();
        long[] xshape = x.shape();
        long[] shape = new long[ndim];
        for (int i = 0; i < ndim; i++) {
            shape[i] = i == 1 || i == ndim - 1 ? xshape[i] : 1;
        }
        return cis.view(shape);
    }
}
