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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static smile.llm.model.inkling.Ix.*;

/**
 * The Inkling-2 DSpark draft ({@code mtp.safetensors}): a block of the last committed token (the anchor) and
 * {@code block - 1} mask rows runs through three dense transformer layers whose attention reads key/value rows computed
 * from the target's features (four taps of the looped forward, mixed per layer by learned weights and RMS-normalized);
 * a recurrent (RNN) Markov head turns the block's hidden rows into {@code block - 1} draft tokens, each conditioned on the
 * previous one. Follows the vLLM implementation ({@code inkling2/nvidia/dspark.py}); the numerics round to bf16 where
 * the reference does.
 *
 * @author Haifeng Li
 */
public final class InklingDraft {
    /** Hyperparameters of {@code mtp_config}. */
    record Config(int hidden, int layers, int heads, int kvHeads, int headDim, int intermediate, double eps, double ropeTheta,
                  int[] local, int window, int block, int rank, int[] taps) { }

    /** Per-request draft state: the context key/value caches {@code [layers][kvHeads, capacity, headDim]} and the last tap. */
    static final class State implements AutoCloseable {
        final Tensor[] k, v;
        final int capacity;
        /** Positions {@code [0, absorbed)} have context rows. */
        int absorbed;
        /** The four taps {@code [4, D]} of the position {@code absorbed - 1} (feeds the Markov head). */
        Tensor lastTap;

        State(Config c, int capacity, Device device) {
            this.capacity = capacity;
            k = new Tensor[c.layers()];
            v = new Tensor[c.layers()];
            for (int i = 0; i < k.length; i++) {
                k[i] = keep(zeros(device, ScalarType.BFloat16, c.kvHeads(), capacity, c.headDim()));
                v[i] = keep(zeros(device, ScalarType.BFloat16, c.kvHeads(), capacity, c.headDim()));
            }
        }

        @Override
        public void close() {
            for (int i = 0; i < k.length; i++) {
                k[i].close();
                v[i].close();
            }
            if (lastTap != null) {
                lastTap.close();
            }
        }
    }

    private final Config c;
    private final InklingModel model;
    private final InklingModelArgs args;
    private final Device device;
    private final int vocab;
    private final Tensor[][] layer;       // per layer: qkv, kvCtx, o, gateUp, down (bf16); input, post, dflare, qNorm, kNorm, kNormCtx (f32)
    private final Tensor alpha;           // softmax(fuse_weight) [layers + 1, taps] f32
    private final Tensor maskEmbed, finalNorm, markovDflare;
    private final Tensor markovW1, rnnState, rnnCtx, rnnBias, foldWeight, foldBias;
    private final float[] invFreq;

    private static final int QKV = 0, KV_CTX = 1, O = 2, GATE_UP = 3, DOWN = 4, IN = 5, POST = 6, DFLARE = 7, QN = 8, KN = 9, KNC = 10;

    private InklingDraft(Config c, InklingModel model, Device device, Map<String, Tensor> t) {
        this.c = c;
        this.model = model;
        this.args = model.args;
        this.device = device;
        this.vocab = args.unpaddedVocabSize();
        layer = new Tensor[c.layers()][];
        for (int i = 0; i < c.layers(); i++) {
            String p = "layers." + i + ".";
            String a = p + "self_attn.";
            layer[i] = new Tensor[]{
                    keep(cat(0, t.get(a + "q_proj.weight"), t.get(a + "k_proj.weight"), t.get(a + "v_proj.weight"))),
                    keep(cat(0, t.get(a + "k_proj_ctx.weight"), t.get(a + "v_proj_ctx.weight"))),
                    keep(t.get(a + "o_proj.weight")),
                    keep(cat(0, t.get(p + "mlp.gate_proj.weight"), t.get(p + "mlp.up_proj.weight"))),
                    keep(t.get(p + "mlp.down_proj.weight")),
                    keep(f32(t.get(p + "input_layernorm.weight"))), keep(f32(t.get(p + "post_attention_layernorm.weight"))),
                    keep(f32(t.get(p + "dflare_norm.weight"))), keep(f32(t.get(a + "q_norm.weight"))),
                    keep(f32(t.get(a + "k_norm.weight"))), keep(f32(t.get(a + "k_norm_ctx.weight")))};
        }
        alpha = keep(o("aten::softmax.int", f32(t.get("fuse_weight")), -1L, null));
        maskEmbed = keep(t.get("mask_embed"));
        finalNorm = keep(f32(t.get("norm.weight")));
        markovDflare = keep(f32(t.get("markov_head.dflare_norm.weight")));
        markovW1 = keep(narrow(t.get("markov_head.markov_w1.weight"), 0, 0, vocab).copy());
        Tensor rnn = t.get("markov_head.rnn_proj.weight");
        rnnState = keep(contiguous(narrow(rnn, 1, 0, 2L * c.rank())));
        rnnCtx = keep(f32(contiguous(narrow(rnn, 1, 2L * c.rank(), 2L * c.hidden()))));
        rnnBias = keep(f32(t.get("markov_head.rnn_proj.bias")));
        // fold hidden_out into the output head: fold(latent) = latent @ (head @ W)^T + head @ b, in float64
        Tensor hw = o("aten::_to_copy", t.get("markov_head.hidden_out.weight"), ScalarType.Double);
        Tensor hb = o("aten::_to_copy", t.get("markov_head.hidden_out.bias"), ScalarType.Double);
        Tensor fw = zeros(device, ScalarType.BFloat16, vocab, c.rank());
        Tensor fb = zeros(device, ScalarType.BFloat16, vocab);
        for (int from = 0; from < vocab; from += 4096) {
            int n = Math.min(4096, vocab - from);
            Tensor head = o("aten::_to_copy", narrow(model.unembed, 0, from, n), ScalarType.Double);
            o("aten::copy_", narrow(fw, 0, from, n), o("aten::mm", head, hw).to(ScalarType.BFloat16));
            o("aten::copy_", narrow(fb, 0, from, n), o("aten::mv", head, hb).to(ScalarType.BFloat16));
            head.close();
        }
        foldWeight = keep(fw);
        foldBias = keep(fb);
        hw.close();
        hb.close();
        invFreq = new float[c.headDim() / 2];
        for (int i = 0; i < invFreq.length; i++) {
            invFreq[i] = (float) (1.0 / Math.pow(c.ropeTheta(), (2.0 * i) / c.headDim()));
        }
    }

    /**
     * Loads the draft of a checkpoint, if it has one.
     *
     * @param dir    checkpoint directory.
     * @param model  the target model (its embedding and output head are shared).
     * @param device the device.
     * @return the draft, or {@code null} when the checkpoint has no {@code mtp.safetensors} or {@code mtp_config}.
     * @throws IOException if the files cannot be read.
     */
    static InklingDraft load(String dir, InklingModel model, Device device) throws IOException {
        File mtp = new File(dir, "mtp.safetensors");
        JsonNode m = new ObjectMapper().readTree(new File(dir, "config.json")).get("mtp_config");
        if (!mtp.exists() || m == null || !"rnn_hidden".equals(m.path("markov_head_type").asString(""))) {
            return null;
        }
        int[] local = new int[m.get("local_layer_ids").size()];
        for (int i = 0; i < local.length; i++) {
            local[i] = m.get("local_layer_ids").get(i).asInt();
        }
        int[] taps = new int[m.get("target_layer_ids").size()];
        for (int i = 0; i < taps.length; i++) {
            taps[i] = m.get("target_layer_ids").get(i).asInt();
        }
        Config c = new Config(m.get("hidden_size").asInt(), m.get("num_hidden_layers").asInt(), m.get("num_attention_heads").asInt(),
                m.get("num_key_value_heads").asInt(), m.get("head_dim").asInt(), m.get("intermediate_size").asInt(),
                m.get("rms_norm_eps").asDouble(), m.get("rope_theta").asDouble(), local, m.get("sliding_window_size").asInt(),
                m.get("block_size").asInt(), m.get("markov_rank").asInt(), taps);
        Set<String> names = new HashSet<>(SafeTensors.listTensors(mtp.getPath()));
        Map<String, Tensor> t = SafeTensors.read(mtp.getPath(), device, names).tensors();
        return new InklingDraft(c, model, device, t);
    }

    /** Draft tokens per round ({@code block - 1}). */
    int drafts() {
        return c.block() - 1;
    }

    /** Target visits whose inputs are tapped, then the final-norm output. */
    int[] taps() {
        return c.taps();
    }

    State newState(int capacity) {
        return new State(c, capacity, device);
    }

    private boolean isLocal(int layer) {
        for (int l : c.local()) {
            if (l == layer) {
                return true;
            }
        }
        return false;
    }

    /** {@code [n, 1, headDim / 2]} cos and sin of positions {@code start ..}. */
    private Tensor[] rope(int start, int n) {
        int half = c.headDim() / 2;
        float[] cos = new float[n * half], sin = new float[n * half];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < half; j++) {
                float angle = (start + i) * invFreq[j];
                cos[i * half + j] = (float) Math.cos(angle);
                sin[i * half + j] = (float) Math.sin(angle);
            }
        }
        return new Tensor[]{Tensor.of(cos, n, 1, half).to(device), Tensor.of(sin, n, 1, half).to(device)};
    }

    /** Neox rotary embedding of {@code [n, heads, headDim]} in float32, returned in bf16. */
    private Tensor applyRope(Tensor x, Tensor cos, Tensor sin) {
        int half = c.headDim() / 2;
        Tensor xf = f32(x);
        Tensor x1 = narrow(xf, 2, 0, half), x2 = narrow(xf, 2, half, half);
        return bf16(cat(2, sub(mul(x1, cos), mul(x2, sin)), add(mul(x2, cos), mul(x1, sin))));
    }

    /** Per-head RMSNorm with a weight, rounded to bf16 ({@code [n, heads, headDim]}). */
    private Tensor headNorm(Tensor x, Tensor w) {
        return bf16(mul(w, unweightedRms(x, c.eps())));
    }

    /** RMSNorm of the alpha-weighted sum of the taps {@code [n, taps, D]} (float32 math), bf16 {@code [n, D]}. */
    private Tensor mix(Tensor taps, int row, Tensor weight) {
        Tensor a = reshape(o("aten::select.int", alpha, 0L, (long) row), 1, -1, 1);
        Tensor mixed = o("aten::sum.dim_IntList", mul(f32(taps), a), new long[]{1}, false);
        return bf16(mul(weight, unweightedRms(mixed, c.eps())));
    }

    /**
     * Stores context key/value rows for newly processed target positions.
     *
     * @param st    the request's draft state.
     * @param taps  bf16 {@code [n, 4, D]}: the target features of positions {@code start .. start + n - 1}.
     * @param start the first position.
     */
    void absorb(State st, Tensor taps, int start) {
        int n = (int) taps.size(0);
        if (start + n > st.capacity) {
            throw new IllegalArgumentException("draft context exceeds the capacity " + st.capacity);
        }
        Tensor[] cs = rope(start, n);
        for (int i = 0; i < c.layers(); i++) {
            Tensor ctx = mix(taps, i, layer[i][DFLARE]);
            Tensor kv = linear(ctx, layer[i][KV_CTX]);
            int kvw = c.kvHeads() * c.headDim();
            Tensor k = headNorm(reshape(narrow(kv, 1, 0, kvw), n, c.kvHeads(), c.headDim()), layer[i][KNC]);
            k = applyRope(k, cs[0], cs[1]);
            Tensor v = reshape(narrow(kv, 1, kvw, kvw), n, c.kvHeads(), c.headDim());
            o("aten::copy_", narrow(st.k[i], 1, start, n), contiguous(transpose(k, 0, 1)));
            o("aten::copy_", narrow(st.v[i], 1, start, n), contiguous(transpose(v, 0, 1)));
        }
        st.absorbed = Math.max(st.absorbed, start + n);
        if (st.lastTap != null) {
            st.lastTap.close();
        }
        st.lastTap = keep(contiguous(o("aten::select.int", taps, 0L, (long) n - 1)).copy());
    }

    private Tensor fusedAddNorm(Tensor[] residual, Tensor x, Tensor w) {
        Tensor sum = add(f32(residual[0]), f32(x));
        residual[0] = bf16(sum);
        return bf16(mul(w, unweightedRms(sum, c.eps())));
    }

    /**
     * Drafts {@code block - 1} tokens.
     *
     * @param st     the request's draft state (context rows up to {@code anchorPos - 1}).
     * @param anchor the last committed token, at position {@code anchorPos} (not yet processed by the target).
     * @param anchorPos its position.
     * @return the draft tokens for positions {@code anchorPos + 1 ..}.
     */
    int[] draft(State st, int anchor, int anchorPos) {
        if (st.absorbed < anchorPos) {
            throw new IllegalStateException("draft context ends at " + st.absorbed + ", the anchor is at " + anchorPos);
        }
        int block = c.block(), hid = c.hidden(), heads = c.heads(), kvh = c.kvHeads(), hd = c.headDim();
        Tensor[] cs = rope(anchorPos, block);
        Tensor ids = Tensor.of(new long[]{anchor}, 1).to(device);
        Tensor anchorEmbed = bf16(rms(o("aten::index_select", model.embed, 0L, ids), model.embedNorm, args.normEps()));
        Tensor x = cat(0, anchorEmbed, o("aten::expand", unsqueeze(maskEmbed, 0), new long[]{block - 1, hid}));
        Tensor[] residual = {null};
        for (int i = 0; i < c.layers(); i++) {
            Tensor[] w = layer[i];
            if (residual[0] == null) {
                residual[0] = x;
                x = rms(x, w[IN], c.eps());
            } else {
                x = fusedAddNorm(residual, x, w[IN]);
            }
            Tensor qkv = linear(x, w[QKV]);
            Tensor q = applyRope(headNorm(reshape(narrow(qkv, 1, 0, heads * hd), block, heads, hd), w[QN]), cs[0], cs[1]);
            Tensor k = applyRope(headNorm(reshape(narrow(qkv, 1, heads * hd, kvh * hd), block, kvh, hd), w[KN]), cs[0], cs[1]);
            Tensor v = reshape(narrow(qkv, 1, (heads + kvh) * hd, kvh * hd), block, kvh, hd);
            o("aten::copy_", narrow(st.k[i], 1, anchorPos, block), contiguous(transpose(k, 0, 1)));
            o("aten::copy_", narrow(st.v[i], 1, anchorPos, block), contiguous(transpose(v, 0, 1)));
            int end = anchorPos + block;
            int s0 = isLocal(i) ? Math.max(0, end - c.window() - block) : 0;
            long s = end - s0;
            int groups = heads / kvh;
            Tensor keys = o("aten::repeat_interleave.self_int", narrow(st.k[i], 1, s0, s), (long) groups, 0L);
            Tensor values = o("aten::repeat_interleave.self_int", narrow(st.v[i], 1, s0, s), (long) groups, 0L);
            Tensor scores = mul(o("aten::matmul", f32(transpose(q, 0, 1)), f32(transpose(keys, 1, 2))), 1.0 / Math.sqrt(hd));
            Tensor probs = o("aten::softmax.int", scores, -1L, null);
            Tensor attn = bf16(o("aten::matmul", probs, f32(values)));                       // [heads, block, hd]
            Tensor flat = reshape(contiguous(transpose(attn, 0, 1)), block, heads * hd);
            Tensor out = linear(flat, w[O]);
            x = fusedAddNorm(residual, out, w[POST]);
            Tensor gu = linear(x, w[GATE_UP]);
            Tensor g = f32(narrow(gu, 1, 0, c.intermediate())), u = f32(narrow(gu, 1, c.intermediate(), c.intermediate()));
            x = linear(bf16(mul(mul(g, sigmoid(g)), u)), w[DOWN]);
        }
        Tensor hidden = bf16(mul(finalNorm, unweightedRms(add(f32(residual[0]), f32(x)), c.eps())));   // [block, D]
        Tensor rows = narrow(hidden, 0, 1, block - 1);
        Tensor base = linear(rows, model.unembed);                                       // bf16 [block - 1, V]
        Tensor fRnn = mix(unsqueeze(st.lastTap, 0), c.layers(), markovDflare);           // [1, D]
        Tensor ctxIn = f32(cat(1, rows, o("aten::expand", fRnn, new long[]{block - 1, hid})));
        Tensor ctx = add(o("aten::matmul", ctxIn, o("aten::t", rnnCtx)), rnnBias);       // [block - 1, 3 * rank]
        Tensor state = zeros(device, ScalarType.BFloat16, 1, c.rank());
        Tensor prev = ids;
        Tensor[] out = new Tensor[block - 1];
        int rank = c.rank();
        for (int i = 0; i < block - 1; i++) {
            Tensor inputs = cat(1, state, o("aten::index_select", markovW1, 0L, prev));          // [1, 2 * rank] bf16
            Tensor raw = add(unsqueeze(o("aten::select.int", ctx, 0L, (long) i), 0),
                    o("aten::matmul", f32(inputs), o("aten::t", f32(rnnState))));
            Tensor r = bf16(raw);
            Tensor gate = bf16(sigmoid(f32(narrow(r, 1, 0, rank))));
            Tensor cand = bf16(tanh(f32(narrow(r, 1, rank, rank))));
            Tensor keepPart = bf16(mul(f32(gate), f32(state)));
            Tensor update = bf16(mul(f32(bf16(o("aten::rsub.Scalar", f32(gate), 1.0))), f32(cand)));
            Tensor latent = bf16(tanh(f32(narrow(r, 1, 2L * rank, rank))));
            state = bf16(add(f32(keepPart), f32(update)));
            Tensor bias = o("aten::addmm", unsqueeze(foldBias, 0), latent, o("aten::t", foldWeight));
            Tensor sum = bf16(add(f32(narrow(base, 0, i, 1)), f32(bias)));
            Tensor capped = o("aten::mul.Scalar", o("aten::tanh", o("aten::div.Scalar", sum, args.finalLogitSoftcap())), args.finalLogitSoftcap());
            prev = reshape(o("aten::argmax", capped, -1L, false), 1);
            out[i] = prev;
        }
        long[] tokens = cat(0, out).to(Device.CPU()).longArray();
        int[] result = new int[tokens.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (int) tokens[i];
        }
        return result;
    }
}
