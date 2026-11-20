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

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;

import static smile.llm.model.glm.Gx.*;

/**
 * Kimi-style delta-rule linear attention (KDA) of GLM-5.3-Flash, reference path (float32, token-sequential
 * recurrence). Per head the state {@code S [K, V]} follows {@code S = S * exp(g); delta = (v - S^T k) * beta;
 * S += k delta^T; o = S^T q} with a per-key-channel decay {@code g}.
 *
 * @author Haifeng Li
 */
final class GlmKda {
    /**
     * The conv and recurrent states of every request slot of one layer.
     *
     * @param conv {@code [slots, kernel - 1, 3 * width]} the last raw (pre-convolution) q/k/v rows.
     * @param rec  {@code [slots, heads, headDim, headDim]} the recurrent state.
     */
    record Pool(Tensor conv, Tensor rec) {
        void reset(int slot) {
            smile.torch.Aten.call("aten::fill_.Scalar", o("aten::select.int", conv, 0L, (long) slot), 0.0);
            smile.torch.Aten.call("aten::fill_.Scalar", o("aten::select.int", rec, 0L, (long) slot), 0.0);
        }
    }

    private final GlmModelArgs args;
    /** Heads and width of this rank's share of the layer. */
    private final int heads;
    private final int width;
    private final Tensor wqkv;      // [3 * width, dim]
    private final Tensor convW;     // [3 * width, kernel]
    private final Tensor wFa;       // [headDim, dim]
    private final Tensor wFb;       // [width, headDim]
    private final Tensor dtBias;    // [width]
    private final Tensor decayRate; // exp(A_log) [heads]
    private final Tensor aLog;      // [heads]
    private final Tensor wBeta;     // [heads, dim]
    private final Tensor wGa;       // [headDim, dim]
    private final Tensor wGb;       // [width, headDim]
    private final Tensor oNorm;     // [headDim]
    private final Tensor wo;        // [dim, width]

    GlmKda(GlmModelArgs args, DeepSeekCheckpoint ck, int layer) {
        this.args = args;
        this.heads = args.kdaHeads() / tpWorld();
        this.width = heads * args.kdaHeadDim();
        String p = args.layerPrefix(layer) + ".self_attn.";
        // tensor parallelism: this rank owns heads [rank * heads, (rank + 1) * heads) of every projection
        this.wqkv = scoped(() -> cat(0, rawShard(ck, p + "q_proj", 0), rawShard(ck, p + "k_proj", 0), rawShard(ck, p + "v_proj", 0)));
        this.convW = scoped(() -> o("aten::reshape", cat(0, rawVectorShard(ck, p + "q_conv1d.weight"),
                rawVectorShard(ck, p + "k_conv1d.weight"), rawVectorShard(ck, p + "v_conv1d.weight")),
                new long[]{3L * width, args.kdaConvKernel()}));
        this.wFa = weight(ck, p + "f_a_proj");
        this.wFb = shard(ck, p + "f_b_proj", 0);
        this.dtBias = vectorShard(ck, p + "dt_bias");
        this.aLog = vectorShard(ck, p + "A_log");
        this.decayRate = scoped(() -> o("aten::exp", aLog));
        this.wBeta = shard(ck, p + "b_proj", 0);
        this.wGa = weight(ck, p + "g_a_proj");
        this.wGb = shard(ck, p + "g_b_proj", 0);
        this.oNorm = vector(ck, p + "o_norm.weight");
        this.wo = shard(ck, p + "o_proj", 1);
    }

    Pool newPool(int slots) {
        int c = 3 * width;
        return new Pool(keep(zeros(ScalarType.Float, slots, args.kdaConvKernel() - 1, c)),
                keep(zeros(ScalarType.Float, slots, heads, args.kdaHeadDim(), args.kdaHeadDim())));
    }

    /**
     * Runs the layer on {@code n} new tokens and advances the state.
     *
     * @param x     {@code [n, dim]} normalized hidden rows.
     * @param pool  the layer's state pool.
     * @param slot  the request's slot (its state is updated in place).
     * @param trace optional capture of intermediates.
     * @return {@code [n, dim]}.
     */
    Tensor forward(Tensor x, Pool pool, int slot, java.util.Map<String, Tensor> trace) {
        Tensor conv0 = o("aten::select.int", pool.conv(), 0L, (long) slot);                 // [kernel - 1, C] view
        Tensor rec0 = o("aten::select.int", pool.rec(), 0L, (long) slot);                   // [H, K, V] view
        long n = x.size(0);
        int h = heads;
        int d = args.kdaHeadDim();
        int kernel = args.kdaConvKernel();

        Tensor raw = linear(x, wqkv);
        if (device().isCUDA() && smile.torch.Native.kdaAvailable() && !"0".equals(System.getenv("GLM_KDA_KERNEL"))) {
            Tensor slotT = Tensor.of(new int[]{slot}, 1).to(device());
            Tensor fgRaw = linear(linear(x, wFa), wFb);
            Tensor betaRaw = linear(x, wBeta);
            Tensor fused = smile.torch.Native.kdaForward(unsqueeze(raw, 0), unsqueeze(fgRaw, 0), unsqueeze(betaRaw, 0),
                    convW, aLog, dtBias, pool.conv(), pool.rec(), slotT, args.kdaGateLowerBound());
            Tensor coreFused = o("aten::reshape", fused, new long[]{n, h, d});
            Tensor gateF = o("aten::reshape", linear(linear(x, wGa), wGb), new long[]{n, h, d});
            Tensor onF = mul(rms(coreFused, oNorm, args.normEps()), sigmoid(gateF));
            return allReduce(linear(o("aten::reshape", onF, new long[]{n, width}), wo));
        }

        // short causal convolution over the concatenated q|k|v rows, carrying the last kernel-1 inputs
        Tensor full = cat(0, conv0, raw);
        Tensor conv = null;
        for (int j = 0; j < kernel; j++) {
            Tensor term = mul(narrow(full, 0, j, n), o("aten::select.int", convW, 1L, (long) j));
            conv = conv == null ? term : add(conv, term);
        }
        conv = o("aten::silu", conv);
        Tensor newConv = o("aten::clone", narrow(full, 0, n, kernel - 1));

        Tensor q = l2norm(o("aten::reshape", narrow(conv, 1, 0, width), new long[]{n, h, d}), 1e-6);
        Tensor k = l2norm(o("aten::reshape", narrow(conv, 1, width, width), new long[]{n, h, d}), 1e-6);
        Tensor v = o("aten::reshape", narrow(conv, 1, 2L * width, width), new long[]{n, h, d});
        q = o("aten::mul.Scalar", q, Math.pow(d, -0.5));

        // forget gate: bound * sigmoid(exp(A_log) * (f_b(f_a(x)) + dt_bias)), one value per key channel
        Tensor fg = o("aten::reshape", add(linear(linear(x, wFa), wFb), dtBias), new long[]{n, h, d});
        Tensor g = o("aten::mul.Scalar", sigmoid(mul(fg, o("aten::reshape", decayRate, new long[]{1, h, 1}))),
                args.kdaGateLowerBound());
        Tensor beta = sigmoid(linear(x, wBeta));    // [n, h]
        if (trace != null) {
            trace.put("g", keep(host(g)));
            trace.put("beta", keep(host(beta)));
        }

        Tensor decay = o("aten::exp", g);
        Tensor s = rec0;
        Tensor[] outs = new Tensor[(int) n];
        for (int t = 0; t < n; t++) {
            Tensor qt = o("aten::select.int", q, 0L, (long) t);                       // [h, d]
            Tensor kt = o("aten::select.int", k, 0L, (long) t);
            Tensor vt = o("aten::select.int", v, 0L, (long) t);
            Tensor bt = unsqueeze(o("aten::select.int", beta, 0L, (long) t), -1);     // [h, 1]
            Tensor dt = unsqueeze(o("aten::select.int", decay, 0L, (long) t), -1);    // [h, d, 1]
            s = mul(s, dt);
            Tensor kv = o("aten::bmm", unsqueeze(kt, 1), s);                          // [h, 1, d]
            Tensor delta = mul(o("aten::sub.Tensor", unsqueeze(vt, 1), kv), unsqueeze(bt, -1));
            s = add(s, o("aten::bmm", unsqueeze(kt, 2), delta));
            outs[t] = o("aten::reshape", o("aten::bmm", unsqueeze(qt, 1), s), new long[]{1, h, d});
        }
        Tensor core = cat(0, outs);                                                   // [n, h, d]
        smile.torch.Aten.call("aten::copy_", conv0, newConv, false);
        smile.torch.Aten.call("aten::copy_", rec0, s, false);

        Tensor gate = o("aten::reshape", linear(linear(x, wGa), wGb), new long[]{n, h, d});
        Tensor on = mul(rms(core, oNorm, args.normEps()), sigmoid(gate));
        return allReduce(linear(o("aten::reshape", on, new long[]{n, width}), wo));
    }

    /**
     * One decode step of {@code B} requests (one token each) in one pass.
     *
     * @param x     {@code [B, dim]} normalized hidden rows.
     * @param pool  the layer's state pool.
     * @param slots the request slots, one per row.
     * @return {@code [B, dim]}.
     */
    Tensor forwardBatch(Tensor x, Pool pool, int[] slots) {
        int b = slots.length;
        if (!(device().isCUDA() && smile.torch.Native.kdaAvailable() && !"0".equals(System.getenv("GLM_KDA_KERNEL")))) {
            Tensor[] rows = new Tensor[b];
            for (int i = 0; i < b; i++) {
                rows[i] = forward(narrow(x, 0, i, 1), pool, slots[i], null);
            }
            return cat(0, rows);
        }
        return forwardBatchKernel(x, pool, Tensor.of(slots, b).to(device()));
    }

    /**
     * The fused-kernel decode step with the slots as a device tensor (no host work, for CUDA-graph capture).
     *
     * @param x     {@code [B, dim]} normalized hidden rows.
     * @param pool  the layer's state pool.
     * @param slotT int32 {@code [B]} the requests' slots.
     * @return {@code [B, dim]}.
     */
    Tensor forwardBatchKernel(Tensor x, Pool pool, Tensor slotT) {
        int b = (int) x.size(0);
        int h = heads;
        int d = args.kdaHeadDim();
        Tensor raw = o("aten::reshape", linear(x, wqkv), new long[]{b, 1, 3L * width});
        Tensor fgRaw = o("aten::reshape", linear(linear(x, wFa), wFb), new long[]{b, 1, width});
        Tensor betaRaw = o("aten::reshape", linear(x, wBeta), new long[]{b, 1, h});
        Tensor fused = smile.torch.Native.kdaForward(raw, fgRaw, betaRaw, convW, aLog, dtBias, pool.conv(), pool.rec(),
                slotT, args.kdaGateLowerBound());
        Tensor core = o("aten::reshape", fused, new long[]{b, h, d});
        Tensor gate = o("aten::reshape", linear(linear(x, wGa), wGb), new long[]{b, h, d});
        Tensor on = mul(rms(core, oNorm, args.normEps()), sigmoid(gate));
        return allReduce(linear(o("aten::reshape", on, new long[]{b, width}), wo));
    }
}
