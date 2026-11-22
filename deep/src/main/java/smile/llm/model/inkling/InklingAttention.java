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

import java.util.Map;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static smile.llm.model.inkling.Ix.*;

/**
 * Attention of one Inkling-2 layer, reference path: no RoPE; the position enters through a learned relative bias
 * (hidden-state dependent), the softmax has a per-query sink logit, the output is gated by a sigmoid of the layer
 * input, and keys and values go through short convolutions. Global layers (one in six) additionally scale queries and
 * relative states by a log-length factor and use input-dependent key/value convolutions; local layers use a sliding
 * window.
 *
 * @author Haifeng Li
 */
final class InklingAttention {
    private final InklingModelArgs args;
    private final boolean local;
    private final int heads;
    private final int kvHeads;
    private final int headDim;
    private final Device device;
    private final Tensor wq, wk, wv, wo, wGate, wRel, wSink, proj;
    private final Tensor qScale, relScale;
    // local layers: static conv kernels [C, taps]; global layers: static [taps, C] + projection [heads * taps, D]
    private final Tensor kConv, vConv, kStatic, vStatic, kProj, vProj;

    InklingAttention(InklingModelArgs args, int layer, Map<String, Tensor> t, Device device) {
        this.args = args;
        this.device = device;
        this.local = args.isLocal(layer);
        this.heads = local ? args.swaHeads() : args.numHeads();
        this.kvHeads = local ? args.swaKvHeads() : args.numKvHeads();
        this.headDim = local ? args.swaHeadDim() : args.headDim();
        String p = "layers." + layer + ".attn.";
        wq = keep(t.get(p + "wq_du.weight"));
        wk = keep(t.get(p + "wk_dv.weight"));
        wv = keep(t.get(p + "wv_dv.weight"));
        wo = keep(t.get(p + "wo_ud.weight"));
        wGate = keep(t.get(p + "gate_attn.weight"));
        wRel = keep(t.get(p + "wr_du.weight"));
        wSink = keep(t.get(p + "attn_sink_proj.weight"));
        proj = keep(t.get(p + "rel_logits_proj.proj"));
        qScale = keep(f32(t.get(p + "q_l2_clip.scale")));
        relScale = keep(f32(t.get(p + "rel_l2_clip.scale")));
        if (local) {
            kConv = keep(f32(reshape(t.get(p + "k_sconv.weight"), -1, args.sconvKernel())));
            vConv = keep(f32(reshape(t.get(p + "v_sconv.weight"), -1, args.sconvKernel())));
            kStatic = vStatic = kProj = vProj = null;
        } else {
            kStatic = keep(f32(t.get(p + "k_sconv.static_w")));
            vStatic = keep(f32(t.get(p + "v_sconv.static_w")));
            kProj = keep(t.get(p + "k_sconv.weight_proj"));
            vProj = keep(t.get(p + "v_sconv.weight_proj"));
            kConv = vConv = null;
        }
    }

    static Iterable<String> names(InklingModelArgs args, int layer) {
        String p = "layers." + layer + ".attn.";
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String s : new String[]{"wq_du.weight", "wk_dv.weight", "wv_dv.weight", "wo_ud.weight", "gate_attn.weight",
                "wr_du.weight", "attn_sink_proj.weight", "rel_logits_proj.proj", "q_l2_clip.scale", "rel_l2_clip.scale"}) {
            names.add(p + s);
        }
        if (args.isLocal(layer)) {
            names.add(p + "k_sconv.weight");
            names.add(p + "v_sconv.weight");
        } else {
            for (String s : new String[]{"k_sconv.static_w", "k_sconv.weight_proj", "v_sconv.static_w", "v_sconv.weight_proj"}) {
                names.add(p + s);
            }
        }
        return names;
    }

    /** {@code 1 + alpha * log(max((position + 1) / n_floor, 1))} per token, {@code [T, 1, 1]} float32. */
    private Tensor logScale(int start, long tokens) {
        float[] tau = new float[(int) tokens];
        for (int i = 0; i < tau.length; i++) {
            float ratio = Math.max((start + i + 1) / (float) args.logScalingNFloor(), 1f);
            tau[i] = 1f + (float) args.logScalingAlpha() * (float) Math.log(ratio);
        }
        return Tensor.of(tau, tokens, 1, 1).to(device);
    }

    /**
     * Runs attention for {@code T} tokens at positions {@code start ..}, extending the application's cache.
     *
     * @param qIn   normalized query input, bf16 {@code [T, D]} (also feeds the gate, sink and relative bias).
     * @param kIn   normalized key input (the same tensor as {@code qIn} in local layers).
     * @param vIn   normalized value input.
     * @param st    the sequence state.
     * @param app   the layer application (selects the cache).
     * @param start position of the first token.
     * @return bf16 {@code [T, D]}.
     */
    Tensor forward(Tensor qIn, Tensor kIn, Tensor vIn, InklingState st, int app, int start) {
        long t = qIn.size(0);
        Tensor tau = local ? null : logScale(start, t);

        // queries: scale, L2 clip, (global) log-length scale
        Tensor q = reshape(linear(qIn, wq), t, heads, headDim);
        q = bf16(args.qkL2ClipThreshold() > 0 ? l2Clip(mul(f32(q), qScale), args.qkL2ClipThreshold()) : f32(q));
        if (!local) {
            q = bf16(mul(f32(q), tau));
        }

        // keys and values: projection, short convolution, L2 clip (keys) / unweighted RMS (values)
        Tensor kp = linear(kIn, wk);
        Tensor vp = linear(vIn, wv);
        Tensor kc;
        Tensor vc;
        if (local) {
            kc = InklingConv.apply(kp, kConv, st.conv[app][InklingState.CONV_K]);
            vc = InklingConv.apply(vp, vConv, st.conv[app][InklingState.CONV_V]);
        } else {
            kc = InklingConv.dynamic(kp, kIn, kStatic, kProj, kvHeads, st.conv[app][InklingState.CONV_K]);
            vc = InklingConv.dynamic(vp, vIn, vStatic, vProj, kvHeads, st.conv[app][InklingState.CONV_V]);
        }
        Tensor k = bf16(l2Clip(f32(reshape(kc, t, kvHeads, headDim)), args.qkL2ClipThreshold()));
        Tensor v = bf16(unweightedRms(reshape(vc, t, kvHeads, headDim), args.normEps()));
        o("aten::copy_", narrow(st.k[app], 1, start, t), contiguous(transpose(k, 0, 1)));
        o("aten::copy_", narrow(st.v[app], 1, start, t), contiguous(transpose(v, 0, 1)));

        // keys visible to the queries
        int window = args.slidingWindow();
        int s0 = local ? Math.max(0, start - window + 1) : 0;
        long s = start + t - s0;
        int groups = heads / kvHeads;
        Tensor keys = o("aten::repeat_interleave.self_int", narrow(st.k[app], 1, s0, s), (long) groups, 0L);
        Tensor values = o("aten::repeat_interleave.self_int", narrow(st.v[app], 1, s0, s), (long) groups, 0L);

        // distance from each query to each key; visibility and the extent of the relative bias
        long[] qpos = new long[(int) t];
        long[] kpos = new long[(int) s];
        for (int i = 0; i < qpos.length; i++) {
            qpos[i] = start + i;
        }
        for (int j = 0; j < kpos.length; j++) {
            kpos[j] = s0 + j;
        }
        Tensor dist = o("aten::sub.Tensor", Tensor.of(qpos, t, 1).to(device), Tensor.of(kpos, 1, s).to(device));
        Tensor masked = o("aten::lt.Scalar", dist, 0L);
        if (local) {
            masked = o("aten::logical_or", masked, o("aten::ge.Scalar", dist, (long) window));
        }
        int extent = args.relExtent();
        Tensor outside = o("aten::logical_or", o("aten::lt.Scalar", dist, 0L), o("aten::ge.Scalar", dist, (long) extent));

        // relative bias: per-token, per-head states mix the learned bias-versus-distance profiles
        Tensor rel = reshape(linear(qIn, wRel), t, heads, args.relDim());
        rel = bf16(l2Clip(mul(f32(rel), relScale), args.relL2ClipThreshold()));
        if (!local) {
            rel = bf16(mul(f32(rel), tau));
        }
        Tensor relLogits = transpose(o("aten::matmul", rel, proj), 0, 1);               // [H, T, extent]
        Tensor index = o("aten::expand", o("aten::clamp", dist, 0L, (long) (extent - 1)), new long[]{heads, t, s});
        Tensor bias = o("aten::masked_fill.Scalar", o("aten::gather", relLogits, 2L, index), outside, 0.0);

        Tensor scores = o("aten::matmul", transpose(q, 0, 1), transpose(keys, 1, 2));    // [H, T, S]
        scores = add(mul(scores, 1.0 / Math.sqrt(headDim)), bias);
        scores = o("aten::masked_fill.Scalar", scores, masked, Double.NEGATIVE_INFINITY);
        Tensor sinks = unsqueeze(transpose(linear(qIn, wSink), 0, 1), -1);               // [H, T, 1]
        Tensor probs = narrow(softmax(f32(cat(2, scores, sinks))), 2, 0, s).to(ScalarType.BFloat16);
        Tensor out = reshape(contiguous(transpose(o("aten::matmul", probs, values), 0, 1)), t, heads * headDim);

        Tensor gate = sigmoid(f32(linear(qIn, wGate)));
        return linear(bf16(mul(f32(out), gate)), wo);
    }
}
