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
package smile.llm.model.qwen;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(GatedDeltaRule.class);
    /** Avoid spamming once per linear layer / forward step. */
    private static final AtomicBoolean javaFallbackWarned = new AtomicBoolean(false);

    private static final SiLU SILU = new SiLU(false);
    private static final Sigmoid SIGMOID = new Sigmoid(false);

    private GatedDeltaRule() {}

    /** Logs the first Java recurrent fallback at WARN; later ones at DEBUG only. */
    private static void logJavaFallbackOnce(String reason) {
        if (javaFallbackWarned.compareAndSet(false, true)) {
            logger.warn("GatedDeltaNet recurrent using Java reference implementation ({})", reason);
        } else if (logger.isDebugEnabled()) {
            logger.debug("GatedDeltaNet recurrent Java fallback: {}", reason);
        }
    }

    /**
     * Softplus matching PyTorch {@code F.softplus}: {@code x} when {@code x > 20},
     * otherwise {@code log(1 + exp(clamp(x, -20, 20)))}.
     *
     * <p>Prefers native {@code torch::softplus} when available.
     */
    static Tensor softplus(Tensor x) {
        Tensor nativeSp = smile.torch.Native.softplus(x);
        if (nativeSp != null) {
            return nativeSp;
        }
        try (Tensor clamped = x.clamp(-20, 20);
             Tensor e = clamped.exp();
             Tensor p = e.add(1.0);
             Tensor sp = p.log();
             Tensor over = x.gt(20.0)) {
            return Tensor.where(over, x, sp);
        }
    }

    /**
     * Decay gate {@code g = -exp(A_log) * softplus(a + dt_bias)} (float output).
     *
     * @param a       projected {@code a} {@code [B,S,H]}
     * @param aLogF   float {@code A_log} {@code [H]}
     * @param dtBiasF float {@code dt_bias} {@code [H]}
     */
    static Tensor computeDecayGate(Tensor a, Tensor aLogF, Tensor dtBiasF) {
        Tensor fused = smile.torch.Native.gatedDeltaComputeG(a, aLogF, dtBiasF);
        if (fused != null) {
            return fused;
        }
        try (Tensor aF = a.to(ScalarType.Float);
             Tensor aPlusDt = aF.add(dtBiasF);
             Tensor soft = softplus(aPlusDt);
             Tensor aExp = aLogF.exp();
             Tensor aNeg = aExp.neg()) {
            return aNeg.mul(soft);
        }
    }

    /**
     * Fused {@code beta = sigmoid(b)} and decay gate {@code g}. Returns
     * {@code [g, beta]}, both float when the native path is used.
     */
    static Tensor[] computeBetaAndDecayGate(Tensor a, Tensor b, Tensor aLogF, Tensor dtBiasF) {
        Tensor[] fused = smile.torch.Native.gatedDeltaComputeGates(a, b, aLogF, dtBiasF);
        if (fused != null) {
            return fused;
        }
        Tensor beta = SIGMOID.forward(b);
        Tensor g = computeDecayGate(a, aLogF, dtBiasF);
        return new Tensor[]{g, beta};
    }

    /** Repeat K/V heads along the head axis ({@code [B,S,H,D] → [B,S,H*rep,D]}). */
    static Tensor repeatHeads(Tensor x, int rep) {
        if (rep <= 1) {
            return x;
        }
        Tensor nativeRep = smile.torch.Native.repeatKvHeads(x, rep);
        if (nativeRep != null) {
            return nativeRep;
        }
        // x: [B, S, Hk, D] → [B, S, Hk*rep, D]
        long[] s = x.shape();
        try (Tensor u = x.unsqueeze(3);
             Tensor e = u.expand(s[0], s[1], s[2], rep, s[3]);
             Tensor viewed = e.reshape(s[0], s[1], s[2] * rep, s[3])) {
            // Contiguous materializes the expand once (same cost as copy).
            return viewed.contiguous();
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

        // Decode S=1: roll state + one weighted sum (no per-k AutoScope loop).
        if (seqLen == 1 && stateLen > 0) {
            return causalConv1dUpdateDecode(hidden, convState, weight, batch, channels, kernel, stateLen);
        }

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor cat = stateLen > 0 ? concatLast3(convState, hidden) : hidden;

            if (stateLen > 0) {
                // Long-lived pool buffer must survive this AutoScope pop.
                convState.detachFromScopes();
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
     * Decode-only causal conv: {@code out = SiLU(sum_k w[c,k] * window[c,k])}
     * where window is {@code concat(state, x)} of length {@code K}, then roll state.
     */
    private static Tensor causalConv1dUpdateDecode(
            Tensor hidden, Tensor convState, Tensor weight,
            long batch, long channels, long kernel, long stateLen) {
        // Prefer fused native update (one kernel + in-place state roll).
        Tensor nativeOut = smile.torch.Native.causalConv1dUpdate(hidden, convState, weight);
        if (nativeOut != null) {
            return nativeOut;
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            convState.detachFromScopes();
            Tensor cat = concatLast3(convState, hidden); // [B,C,K]
            Tensor w = weight.dim() == 3 ? weight.reshape(channels, kernel) : weight;
            // out[b,c,0] = sum_k cat[b,c,k] * w[c,k]
            Tensor wUnsq = w.unsqueeze(0); // [1,C,K]
            Tensor prod = cat.mul(wUnsq);
            Tensor summed = prod.sum(-1, true); // [B,C,1]
            Tensor activated = SILU.forward(summed);
            try (var span = Index.slice(1, (int) kernel);
                 Tensor tail = cat.get(Index.Colon, Index.Colon, span)) {
                convState.put_(tail, Index.Colon, Index.Colon, Index.Colon);
            }
            activated.promoteToParent();
            return activated;
        } finally {
            Tensor.pop();
        }
    }

    /**
     * Decode {@code S==1}: fused causal conv + QKV channel split + K/V head repeat.
     *
     * @param hidden      {@code [B, C, 1]} mixed QKV projection.
     * @param convState   {@code [B, C, K-1]} (updated in place).
     * @param weight      {@code [C, K]} depthwise kernel.
     * @return {@code [query, key, value]} with shapes
     *         {@code [B,1,numVHeads,headKDim]} and {@code [B,1,numVHeads,headVDim]},
     *         or {@code null} when the native path is unavailable.
     */
    static Tensor[] causalConv1dUpdateSplitQkv(
            Tensor hidden, Tensor convState, Tensor weight,
            int numKHeads, int numVHeads, int headKDim, int headVDim) {
        Tensor[] fused = smile.torch.Native.causalConv1dUpdateSplitQkv(
                hidden, convState, weight, numKHeads, numVHeads, headKDim, headVDim);
        if (fused != null) {
            convState.detachFromScopes();
            return fused;
        }
        long batch = hidden.shape()[0];
        long channels = hidden.shape()[1];
        long kernel = weight.shape()[weight.dim() - 1];
        long stateLen = convState.shape()[2];
        Tensor mixedConvBase = causalConv1dUpdateDecode(
                hidden, convState, weight, batch, channels, kernel, stateLen);
        Tensor mixedConv = mixedConvBase.reshape(batch, 1, channels);
        try (var qSpan = Index.slice(0, headKDim * numKHeads);
             var kSpan = Index.slice(headKDim * numKHeads, 2 * headKDim * numKHeads);
             var vSpan = Index.slice(2 * headKDim * numKHeads, 2 * headKDim * numKHeads + headVDim * numVHeads)) {
            Tensor qSlice = mixedConv.get(Index.Ellipsis, qSpan);
            Tensor query = qSlice.view(batch, 1, numKHeads, headKDim);
            Tensor kSlice = mixedConv.get(Index.Ellipsis, kSpan);
            Tensor key = kSlice.view(batch, 1, numKHeads, headKDim);
            Tensor vSlice = mixedConv.get(Index.Ellipsis, vSpan);
            Tensor value = vSlice.view(batch, 1, numVHeads, headVDim);
            int rep = numVHeads / numKHeads;
            if (rep > 1) {
                Tensor qRep = repeatHeads(query, rep);
                Tensor kRep = repeatHeads(key, rep);
                if (qRep != query) {
                    query.close();
                }
                if (kRep != key) {
                    key.close();
                }
                query = qRep;
                key = kRep;
            }
            qSlice.close();
            kSlice.close();
            vSlice.close();
            mixedConv.close();
            mixedConvBase.close();
            return new Tensor[]{query, key, value};
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
                // Long-lived pool buffer must survive this AutoScope pop.
                convState.detachFromScopes();
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
     * <p>When {@code initialState} is non-null, it is updated in place (float
     * pool) or via a short-lived float workspace written back (bf16/fp16 pool).
     * Prefer passing the {@link DeltaNetStatePool} recurrent buffer for both
     * prefill and decode so each layer does not allocate a fresh state tensor.
     *
     * <p>On CUDA (or any build with the native op), prefers the fused
     * {@code smile_recurrent_gated_delta_rule} path; falls back to the Java
     * reference implementation otherwise.
     *
     * @param query         {@code [B, S, H, Dk]}
     * @param key           {@code [B, S, H, Dk]}
     * @param value         {@code [B, S, H, Dv]}
     * @param g             decay logits {@code [B, S, H]} (already includes A_log scaling)
     * @param beta          input gate {@code [B, S, H]}
     * @param initialState  {@code [B, H, Dk, Dv]} or {@code null}
     * @param outputState   when true, persist final state (in-place / write-back);
     *                      returned second element is non-null only when a new
     *                      tensor must be installed by the caller (no {@code initialState})
     * @param qkL2norm      apply L2 norm to Q/K inside the kernel
     * @return (output {@code [B,S,H,Dv]}, finalState or null)
     */
    static smile.util.Tuple2<Tensor, Tensor> recurrentGatedDeltaRule(
            Tensor query, Tensor key, Tensor value, Tensor g, Tensor beta,
            Tensor initialState, boolean outputState, boolean qkL2norm) {

        // Fast path: fused native kernel when we have a float pool state.
        if (initialState != null && initialState.dtype() == ScalarType.Float && outputState) {
            if (initialState.shape()[0] != query.shape()[0]) {
                throw new IllegalArgumentException(String.format(
                        "DeltaNet state batch=%d != query batch=%d (use activeRecurrent, not full pool)",
                        initialState.shape()[0], query.shape()[0]));
            }
            initialState.detachFromScopes();
            Tensor nativeOut;
            try {
                nativeOut = smile.torch.Native.recurrentGatedDeltaRule(
                        query, key, value, g, beta, initialState, qkL2norm);
            } catch (RuntimeException ex) {
                // Older libsmile_torch without GPU libtorch fallback, etc.
                logJavaFallbackOnce(ex.getMessage() != null ? ex.getMessage() : ex.toString());
                return recurrentGatedDeltaRuleJava(
                        query, key, value, g, beta, initialState, outputState, qkL2norm);
            }
            if (nativeOut != null) {
                // Belt-and-suspenders: keep activations in the compute dtype.
                if (nativeOut.dtype() != query.dtype()) {
                    Tensor cast = nativeOut.to(query.dtype());
                    nativeOut.close();
                    nativeOut = cast;
                }
                return new smile.util.Tuple2<>(nativeOut, null);
            }
            logJavaFallbackOnce("native recurrentGatedDeltaRule returned null");
        }

        return recurrentGatedDeltaRuleJava(
                query, key, value, g, beta, initialState, outputState, qkL2norm);
    }

    /** Java reference implementation (CPU tests / native fallback). */
    static smile.util.Tuple2<Tensor, Tensor> recurrentGatedDeltaRuleJava(
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
            Tensor qScaled = q.mul(scale);
            if (qScaled != q) {
                q.close();
                q = qScaled;
            }

            var opts = new Tensor.Options()
                    .device(query.device())
                    .dtype(ScalarType.Float)
                    .requireGradients(false);

            final boolean writeBackToInitial;
            Tensor state;
            if (initialState == null) {
                state = Tensor.zeros(opts, batch, heads, kDim, vDim);
                writeBackToInitial = false;
            } else if (initialState.dtype() == ScalarType.Float) {
                initialState.detachFromScopes();
                state = initialState;
                writeBackToInitial = false;
            } else {
                state = initialState.to(ScalarType.Float);
                writeBackToInitial = outputState;
            }

            Tensor out = Tensor.zeros(opts, batch, heads, seqLen, vDim);

            for (int t = 0; t < seqLen; t++) {
                AutoScope stepScope = new AutoScope();
                Tensor.push(stepScope);
                try (var tIdx = Index.of(t)) {
                    Tensor qStep = q.get(Index.Colon, Index.Colon, tIdx);
                    Tensor kStep = k.get(Index.Colon, Index.Colon, tIdx);
                    Tensor vStep = v.get(Index.Colon, Index.Colon, tIdx);
                    Tensor gTok = gF.get(Index.Colon, Index.Colon, tIdx).exp();
                    Tensor betaStep = betaF.get(Index.Colon, Index.Colon, tIdx).unsqueeze(-1);

                    try (Tensor gView = gTok.view(batch, heads, 1, 1);
                         Tensor decayed = state.mul(gView)) {
                        smile.torch.Native.copy_(state, decayed);
                    }

                    Tensor kRow = kStep.unsqueeze(-2);
                    Tensor kv = kRow.matmul(state);
                    Tensor kvMem = kv.reshape(batch, heads, vDim);

                    Tensor delta = vStep.sub(kvMem).mul(betaStep);
                    Tensor kUnsq = kStep.unsqueeze(-1);
                    Tensor deltaUnsq = delta.unsqueeze(-2);
                    try (Tensor kDelta = kUnsq.mul(deltaUnsq)) {
                        state.add_(kDelta);
                    }

                    Tensor qRow = qStep.unsqueeze(-2);
                    Tensor yFull = qRow.matmul(state);
                    Tensor y = yFull.reshape(batch, heads, vDim);
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
                if (writeBackToInitial) {
                    try (Tensor asPoolDtype = state.to(initialState.dtype())) {
                        smile.torch.Native.copy_(initialState, asPoolDtype);
                    }
                } else if (initialState == null) {
                    finalState = state.to(query.dtype());
                    finalState.promoteToParent();
                }
            }
            core.promoteToParent();
            return new smile.util.Tuple2<>(core, finalState);
        } finally {
            Tensor.pop();
        }
    }
}
