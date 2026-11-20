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

import java.util.HashMap;
import java.util.Map;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;
import smile.llm.model.deepseek.DeepSeekOps;

import static smile.llm.model.glm.Gx.*;

/**
 * One decoder layer: manifold-constrained hyper-connections around a token mixer (KDA or MLA) and a feed-forward.
 * The residual is {@code hcMult} parallel streams {@code [n, hc, dim]}; per sublayer the streams are collapsed to
 * the block input ({@code pre}), the block output is spread over the streams ({@code post}) and the old streams
 * are mixed with a doubly-stochastic matrix ({@code comb}).
 *
 * @author Haifeng Li
 */
final class GlmBlock {
    /** The learned projection of one hyper-connection. */
    private record Hc(Tensor fn, Tensor base, Tensor scale) { }

    private final GlmModelArgs args;
    final int layer;
    private final GlmKda kda;
    private final GlmMla mla;
    private final GlmMlp mlp;
    private final Hc attnHc;
    private final Hc ffnHc;
    private final Tensor inputNorm;
    private final Tensor postNorm;

    GlmBlock(GlmModelArgs args, DeepSeekCheckpoint ck, int layer) {
        this.args = args;
        this.layer = layer;
        String p = args.layerPrefix(layer) + ".";
        this.kda = args.isSparseAttention(layer) ? null : new GlmKda(args, ck, layer);
        this.mla = args.isSparseAttention(layer) ? new GlmMla(args, ck, layer) : null;
        this.mlp = new GlmMlp(args, ck, layer);
        this.attnHc = new Hc(vector(ck, p + "hc_attn_fn"), vector(ck, p + "hc_attn_base"), vector(ck, p + "hc_attn_scale"));
        this.ffnHc = new Hc(vector(ck, p + "hc_ffn_fn"), vector(ck, p + "hc_ffn_base"), vector(ck, p + "hc_ffn_scale"));
        this.inputNorm = vector(ck, p + "input_layernorm.weight");
        this.postNorm = vector(ck, p + "post_attention_layernorm.weight");
    }

    /** The layer's KDA state pool ({@code null} for an MLA layer). */
    GlmKda.Pool kdaPool;

    /** Allocates the KDA state pool for {@code slots} concurrent requests. */
    void allocate(int slots) {
        if (kda != null) {
            kdaPool = kda.newPool(slots);
        }
    }

    GlmMla.State newMlaState() {
        return mla == null ? null : mla.newState();
    }

    /**
     * Hyper-connection weights of a sublayer.
     *
     * @param streams {@code [n, hc, dim]}.
     * @return {@code {post [n, hc], comb [n, hc, hc], collapsed [n, dim]}}.
     */
    private Tensor[] hc(Tensor streams, Hc w) {
        long n = streams.size(0);
        int hc = args.hcMult();
        Tensor flat = o("aten::reshape", f32(streams), new long[]{n, (long) hc * args.dim()});
        Tensor normed = rms(flat, null, args.normEps());
        Tensor mixes = linear(normed, w.fn());
        Tensor[] r = DeepSeekOps.hcSplitSinkhorn(mixes, w.scale(), w.base(), hc, args.hcSinkhornIters(), args.hcEps());
        Tensor collapsed = sumDim(mul(unsqueeze(r[0], -1), f32(streams)), 1, false);
        return new Tensor[]{r[1], r[2], collapsed};
    }

    /** {@code post * out + comb^T @ residual}. */
    private static Tensor expand(Tensor post, Tensor comb, Tensor out, Tensor residual) {
        Tensor mixed = o("aten::matmul", o("aten::transpose.int", comb, 1L, 2L), residual);
        return add(mul(unsqueeze(post, -1), unsqueeze(out, 1)), mixed);
    }

    /**
     * Runs the layer.
     *
     * @param streams {@code [n, hc, dim]}.
     * @param slot    the request's slot in the KDA pools.
     * @param mlaState the request's MLA state of this layer ({@code null} for a KDA layer).
     * @param trace   optional capture ({@code L<i>.*} keys).
     * @return the new streams.
     */
    Tensor forward(Tensor streams, int slot, GlmMla.State mlaState, Map<String, Tensor> trace) {
        String key = "L" + layer + ".";
        Tensor[] a = hc(streams, attnHc);
        Tensor x = rms(a[2], inputNorm, args.normEps());
        Map<String, Tensor> inner = trace == null ? null : new HashMap<>();
        Tensor attn = kda != null ? kda.forward(x, kdaPool, slot, inner) : mla.forward(x, mlaState, inner);
        Tensor mid = expand(a[0], a[1], attn, f32(streams));

        Tensor[] f = hc(mid, ffnHc);
        Tensor y = rms(f[2], postNorm, args.normEps());
        Tensor ffn = mlp.forward(y, inner);
        Tensor out = expand(f[0], f[1], ffn, mid);

        if (trace != null) {
            trace.put(key + "attn_hc.post", keep(host(a[0])));
            trace.put(key + "attn_hc.comb", keep(host(a[1])));
            trace.put(key + "attn_hc.collapsed", keep(host(a[2])));
            trace.put(key + "attn_in", keep(host(x)));
            trace.put(key + "attn", keep(host(attn)));
            trace.put(key + "ffn_hc.collapsed", keep(host(f[2])));
            trace.put(key + "ffn", keep(host(ffn)));
            trace.put(key + "out", keep(host(out)));
            for (var e : inner.entrySet()) {
                trace.put(key + e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /**
     * Runs the layer on one decode token of each of several requests.
     *
     * @param streams   {@code [B, hc, dim]}.
     * @param slots     the requests' KDA slots.
     * @param mlaStates the requests' MLA states of this layer ({@code null} for a KDA layer).
     * @return the new streams.
     */
    Tensor forwardBatch(Tensor streams, int[] slots, GlmMla.State[] mlaStates) {
        Tensor[] a = hc(streams, attnHc);
        Tensor x = rms(a[2], inputNorm, args.normEps());
        Tensor attn = kda != null ? kda.forwardBatch(x, kdaPool, slots) : mla.forwardBatch(x, mlaStates);
        Tensor mid = expand(a[0], a[1], attn, f32(streams));
        Tensor[] f = hc(mid, ffnHc);
        Tensor y = rms(f[2], postNorm, args.normEps());
        return expand(f[0], f[1], mlp.forward(y, null), mid);
    }

    /**
     * The decode step on device tensors only (CUDA-graph capture).
     *
     * @param streams  {@code [B, hc, dim]}.
     * @param slots    int32 {@code [B]} KDA slots.
     * @param tables   int64 {@code [3, B]} latent, pooled-key and ring addresses of this layer (MLA layers only).
     * @param lens     int32 {@code [B]} tokens before this step.
     * @param maxPools row stride of the score buffer.
     * @return the new streams.
     */
    Tensor forwardGraph(Tensor streams, Tensor slots, Tensor tables, Tensor lens, long maxPools) {
        Tensor[] a = hc(streams, attnHc);
        Tensor x = rms(a[2], inputNorm, args.normEps());
        Tensor attn;
        if (kda != null) {
            attn = kda.forwardBatchKernel(x, kdaPool, slots);
        } else {
            attn = mla.forwardGraph(x, o("aten::select.int", tables, 0L, 0L), o("aten::select.int", tables, 0L, 1L),
                    o("aten::select.int", tables, 0L, 2L), lens, maxPools);
        }
        Tensor mid = expand(a[0], a[1], attn, f32(streams));
        Tensor[] f = hc(mid, ffnHc);
        Tensor y = rms(f[2], postNorm, args.normEps());
        return expand(f[0], f[1], mlp.forward(y, null), mid);
    }

    /** Whether the layer is an MLA layer (has pointer tables in a graph). */
    boolean isMla() {
        return mla != null;
    }

    /** Makes the MLA caches of a state ready for one more token. */
    static void reserve(GlmMla.State st) {
        st.latent.ensure(st.length + 1);
        st.pool.ensure((st.length >> 2) + 1);
    }

    /** Fills the three table rows of a request: latent, pooled-key and ring addresses. */
    static void addresses(GlmMla.State st, long[] out, int i, int nb) {
        out[i] = st.latent.pointer();
        out[nb + i] = st.pool.pointer();
        out[2 * nb + i] = smile.torch.Native.dsDataPtrs(new Tensor[]{st.ring})[0];
    }

    /** The host bookkeeping of a decode step that ran on the device. */
    static void commit(GlmMla.State st) {
        st.length++;
        st.latent.setLength(st.length);
        st.pool.setLength(st.length >> 2);
    }
}
