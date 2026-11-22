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
package smile.llm.model.inkling;

import java.util.function.Supplier;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.util.AutoScope;

/**
 * Small tensor helpers for the Inkling reference path. Every op follows the dtype flow of the Hugging Face model
 * (bf16 matmuls, float32 norms, gates and routing) so that results match it up to GEMM accumulation order.
 *
 * @author Haifeng Li
 */
final class Ix {
    private Ix() { }

    static Tensor o(String op, Object... args) {
        return Aten.one(op, args);
    }

    static Tensor f32(Tensor x) {
        return x.dtype() == ScalarType.Float ? x : x.to(ScalarType.Float);
    }

    static Tensor bf16(Tensor x) {
        return x.dtype() == ScalarType.BFloat16 ? x : x.to(ScalarType.BFloat16);
    }

    static Tensor narrow(Tensor t, long dim, long start, long length) {
        return o("aten::narrow", t, dim, start, length);
    }

    static Tensor cat(long dim, Tensor... ts) {
        return o("aten::cat", ts, dim);
    }

    static Tensor reshape(Tensor t, long... shape) {
        return o("aten::reshape", t, shape);
    }

    static Tensor transpose(Tensor t, long a, long b) {
        return o("aten::transpose.int", t, a, b);
    }

    static Tensor unsqueeze(Tensor t, long dim) {
        return o("aten::unsqueeze", t, dim);
    }

    static Tensor contiguous(Tensor t) {
        return o("aten::contiguous", t);
    }

    static Tensor mul(Tensor a, Tensor b) {
        return o("aten::mul.Tensor", a, b);
    }

    static Tensor mul(Tensor a, double s) {
        return o("aten::mul.Scalar", a, s);
    }

    static Tensor add(Tensor a, Tensor b) {
        return o("aten::add.Tensor", a, b);
    }

    static Tensor sub(Tensor a, Tensor b) {
        return o("aten::sub.Tensor", a, b);
    }

    static Tensor sigmoid(Tensor x) {
        return o("aten::sigmoid", x);
    }

    static Tensor tanh(Tensor x) {
        return o("aten::tanh", x);
    }

    /** {@code x w^T} with the dtype of {@code x} and {@code w} (bf16 or float32; no bias). */
    static Tensor linear(Tensor x, Tensor w) {
        return o("aten::linear", x, w);
    }

    /** Softmax over the last dimension in float32. */
    static Tensor softmax(Tensor x) {
        return o("aten::softmax.int", x, -1L, ScalarType.Float);
    }

    /** {@code x * rsqrt(mean(x^2) + eps)} over the last dimension in float32 (output float32). */
    static Tensor unweightedRms(Tensor x, double eps) {
        Tensor xf = f32(x);
        Tensor var = o("aten::mean.dim", mul(xf, xf), new long[]{-1}, true);
        return mul(xf, o("aten::rsqrt", o("aten::add.Scalar", var, eps)));
    }

    /** Weighted RMSNorm: float32 computation, {@code weight * normalized}, returned in the dtype of {@code x}. */
    static Tensor rms(Tensor x, Tensor weight, double eps) {
        Tensor y = mul(weight, unweightedRms(x, eps));
        return x.dtype() == ScalarType.Float ? y : y.to(x.dtype());
    }

    /** Scales each last-dimension vector of a float32 tensor down to norm {@code threshold} (shorter ones are unchanged). */
    static Tensor l2Clip(Tensor xf, double threshold) {
        Tensor norm = o("aten::linalg_vector_norm", xf, 2.0, new long[]{-1}, true, null);
        Tensor factor = o("aten::clamp", o("aten::div.Tensor", o("aten::full_like", norm, threshold),
                o("aten::add.Scalar", norm, 1e-6)), null, 1.0);
        return mul(xf, factor);
    }

    /** Long-lived tensor: out of every scope. */
    static Tensor keep(Tensor t) {
        t.detachFromScopes();
        return t;
    }

    /** Builds a weight in its own scope so that the temporaries are freed at once. */
    static Tensor scoped(Supplier<Tensor> build) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor t = build.get();
            t.promoteToParent();
            return keep(t);
        } finally {
            Tensor.pop();
        }
    }

    /** Runs a step in its own scope; the returned tensor (and nothing else) survives into the caller's scope. */
    static Tensor stepScoped(Supplier<Tensor> step) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor t = step.get();
            t.promoteToParent();
            return t;
        } finally {
            Tensor.pop();
        }
    }

    static Tensor zeros(Device device, ScalarType dtype, long... shape) {
        return Tensor.zeros(new Tensor.Options().dtype(dtype).device(device), shape);
    }
}
