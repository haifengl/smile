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
package smile.llm.qwen;

import smile.deep.activation.SiLU;
import smile.deep.activation.Sigmoid;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.util.AutoScope;

/**
 * Reference implementations of Gated DeltaNet kernels (HuggingFace torch path).
 *
 * <p>Prefill and decode both use the recurrent rule for numerical fidelity;
 * a chunked path can be added later for throughput.
 *
 * @author Haifeng Li
 */
final class GatedDeltaRule {
    private static final SiLU SILU = new SiLU(false);
    private static final Sigmoid SIGMOID = new Sigmoid(false);

    private GatedDeltaRule() {}

    /** Softplus via {@code log(1 + exp(x))} with a clamp for stability. */
    static Tensor softplus(Tensor x) {
        try (Tensor clamped = x.clamp(-20, 20);
             Tensor e = clamped.exp();
             Tensor p = e.add(1.0)) {
            return p.log();
        }
    }

    /** L2-normalize along the last dimension. */
    static Tensor l2norm(Tensor x) {
        try (Tensor x2 = x.mul(x);
             Tensor s = x2.sum(-1, true);
             Tensor inv = s.add(1e-6).rsqrt_()) {
            return x.mul(inv);
        }
    }

    /**
     * Concatenate {@code a} and {@code b} on the last axis (3-D tensors).
     */
    static Tensor concatLast3(Tensor a, Tensor b) {
        long[] as = a.shape();
        long[] bs = b.shape();
        long rows = as[0] * as[1];
        try (Tensor a2 = a.reshape(rows, as[2]);
             Tensor b2 = b.reshape(rows, bs[2]);
             Tensor cat = Tensor.hstack(a2, b2);
             Tensor viewed = cat.reshape(as[0], as[1], as[2] + bs[2])) {
            // Must copy: try-with closes cat/viewed; a reshape view would dangle.
            return viewed.copy();
        }
    }

    /**
     * Depthwise causal conv1d update (HF {@code torch_causal_conv1d_update}).
     *
     * @param hidden    {@code [B, C, L]} new tokens.
     * @param convState {@code [B, C, K-1]} left context (updated in place).
     * @param weight    {@code [C, K]} depthwise kernel (no bias).
     * @return SiLU-activated conv output {@code [B, C, L]}.
     */
    static Tensor causalConv1dUpdate(Tensor hidden, Tensor convState, Tensor weight) {
        long batch = hidden.shape()[0];
        long channels = hidden.shape()[1];
        long seqLen = hidden.shape()[2];
        long kernel = weight.shape()[weight.dim() - 1];
        long stateLen = convState != null ? convState.shape()[2] : 0;
        if (stateLen != kernel - 1) {
            throw new IllegalArgumentException("convState length must be kernel-1");
        }

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor cat = stateLen > 0 ? concatLast3(convState, hidden) : hidden;

            if (stateLen > 0) {
                long total = cat.shape()[2];
                try (var span = Index.slice(total - stateLen, total);
                     Tensor tail = cat.get(Index.Colon, Index.Colon, span)) {
                    convState.put_(tail, Index.Colon, Index.Colon, Index.Colon);
                }
            }

            Tensor out = Tensor.zeros(
                    new Tensor.Options().device(hidden.device()).dtype(hidden.dtype()).requireGradients(false),
                    batch, channels, seqLen);

            Tensor w = weight.dim() == 3 ? weight.reshape(channels, kernel) : weight;
            for (int k = 0; k < kernel; k++) {
                try (var span = Index.slice(k, k + (int) seqLen);
                     Tensor slice = cat.get(Index.Colon, Index.Colon, span);
                     var wIdx = Index.of(k);
                     Tensor wk = w.get(Index.Colon, wIdx).unsqueeze(-1);
                     Tensor term = slice.mul(wk)) {
                    out.add_(term);
                }
            }
            Tensor activated = SILU.forward(out);
            activated.promoteToParent();
            return activated;
        } finally {
            Tensor.pop();
        }
    }

    /**
     * Causal depthwise conv for a full prefill (zero left context, then store state).
     */
    static Tensor causalConv1dPrefill(Tensor hidden, Tensor convState, Tensor weight) {
        long kernel = weight.shape()[weight.dim() - 1];
        long stateLen = kernel - 1;
        long batch = hidden.shape()[0];
        long channels = hidden.shape()[1];
        long seqLen = hidden.shape()[2];

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor padded;
            if (stateLen > 0) {
                Tensor left = Tensor.zeros(
                        new Tensor.Options().device(hidden.device()).dtype(hidden.dtype()).requireGradients(false),
                        batch, channels, stateLen);
                padded = concatLast3(left, hidden);
            } else {
                padded = hidden;
            }

            Tensor out = Tensor.zeros(
                    new Tensor.Options().device(hidden.device()).dtype(hidden.dtype()).requireGradients(false),
                    batch, channels, seqLen);
            Tensor w = weight.dim() == 3 ? weight.reshape(channels, kernel) : weight;
            for (int k = 0; k < kernel; k++) {
                try (var span = Index.slice(k, k + (int) seqLen);
                     Tensor slice = padded.get(Index.Colon, Index.Colon, span);
                     var wIdx = Index.of(k);
                     Tensor wk = w.get(Index.Colon, wIdx).unsqueeze(-1);
                     Tensor term = slice.mul(wk)) {
                    out.add_(term);
                }
            }

            if (convState != null && stateLen > 0) {
                long total = padded.shape()[2];
                try (var span = Index.slice(total - stateLen, total);
                     Tensor tail = padded.get(Index.Colon, Index.Colon, span)) {
                    convState.put_(tail, Index.Colon, Index.Colon, Index.Colon);
                }
            }
            Tensor activated = SILU.forward(out);
            activated.promoteToParent();
            return activated;
        } finally {
            Tensor.pop();
        }
    }

    /**
     * Recurrent gated delta rule (HF {@code torch_recurrent_gated_delta_rule}).
     *
     * @param query         {@code [B, S, H, Dk]}
     * @param key           {@code [B, S, H, Dk]}
     * @param value         {@code [B, S, H, Dv]}
     * @param g             decay logits {@code [B, S, H]} (already includes A_log scaling)
     * @param beta          input gate {@code [B, S, H]}
     * @param initialState  {@code [B, H, Dk, Dv]} or {@code null}
     * @param outputState   when true, returns final state as second element
     * @param qkL2norm      apply L2 norm to Q/K inside the kernel
     * @return (output {@code [B,S,H,Dv]}, finalState or null)
     */
    static smile.util.Tuple2<Tensor, Tensor> recurrentGatedDeltaRule(
            Tensor query, Tensor key, Tensor value, Tensor g, Tensor beta,
            Tensor initialState, boolean outputState, boolean qkL2norm) {

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor q = query;
            Tensor k = key;
            if (qkL2norm) {
                q = l2norm(q);
                k = l2norm(k);
            }

            Tensor qT = q.transpose(1, 2);
            Tensor qC = qT.contiguous();
            q = qC.to(ScalarType.Float);
            Tensor kT = k.transpose(1, 2);
            Tensor kC = kT.contiguous();
            k = kC.to(ScalarType.Float);
            Tensor vT = value.transpose(1, 2);
            Tensor vC = vT.contiguous();
            Tensor v = vC.to(ScalarType.Float);
            Tensor betaT = beta.transpose(1, 2);
            Tensor betaC = betaT.contiguous();
            Tensor betaF = betaC.to(ScalarType.Float);
            Tensor gT = g.transpose(1, 2);
            Tensor gC = gT.contiguous();
            Tensor gF = gC.to(ScalarType.Float);

            long batch = k.shape()[0];
            long heads = k.shape()[1];
            long seqLen = k.shape()[2];
            long kDim = k.shape()[3];
            long vDim = v.shape()[3];
            double scale = 1.0 / Math.sqrt(kDim);
            q = q.mul(scale);

            var opts = new Tensor.Options()
                    .device(query.device())
                    .dtype(ScalarType.Float)
                    .requireGradients(false);
            Tensor state = initialState == null
                    ? Tensor.zeros(opts, batch, heads, kDim, vDim)
                    : initialState.to(ScalarType.Float);

            Tensor out = Tensor.zeros(opts, batch, heads, seqLen, vDim);

            // Reuse one state buffer in place; free per-step workspace immediately.
            for (int t = 0; t < seqLen; t++) {
                AutoScope stepScope = new AutoScope();
                Tensor.push(stepScope);
                try (var tIdx = Index.of(t)) {
                    Tensor qStep = q.get(Index.Colon, Index.Colon, tIdx);
                    Tensor kStep = k.get(Index.Colon, Index.Colon, tIdx);
                    Tensor vStep = v.get(Index.Colon, Index.Colon, tIdx);
                    Tensor gStep = gF.get(Index.Colon, Index.Colon, tIdx).exp()
                            .unsqueeze(-1).unsqueeze(-1);
                    Tensor betaStep = betaF.get(Index.Colon, Index.Colon, tIdx).unsqueeze(-1);
                    // Decay and write-back in place to avoid a second full state buffer.
                    state.mul_(gStep);
                    Tensor kUnsq = kStep.unsqueeze(-1);
                    Tensor kvMem = state.mul(kUnsq).sum(-2, false);
                    Tensor delta = vStep.sub(kvMem).mul(betaStep);
                    Tensor deltaUnsq = delta.unsqueeze(-2);
                    try (Tensor kDelta = kUnsq.mul(deltaUnsq)) {
                        state.add_(kDelta);
                    }
                    Tensor y = state.mul(qStep.unsqueeze(-1)).sum(-2, false);
                    out.put_(y, Index.Colon, Index.Colon, tIdx);
                } finally {
                    Tensor.pop();
                }
            }

            Tensor coreT = out.transpose(1, 2);
            Tensor coreC = coreT.contiguous();
            Tensor core = coreC.to(query.dtype());
            Tensor finalState = null;
            if (outputState) {
                finalState = state.to(query.dtype());
                finalState.promoteToParent();
            }
            core.promoteToParent();
            return new smile.util.Tuple2<>(core, finalState);
        } finally {
            Tensor.pop();
        }
    }
}
