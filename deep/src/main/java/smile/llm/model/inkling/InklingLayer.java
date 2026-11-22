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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

import static smile.llm.model.inkling.Ix.*;

/**
 * One decoder layer of Inkling-2 (reference path): attention and a routed-expert MLP, each reading its input from the
 * AttnRes block states, each followed by a short convolution. Layers in the loop additionally blend their inputs
 * with the messages of the previous pass through rank-limited gates.
 *
 * @author Haifeng Li
 */
final class InklingLayer {
    /** Gate order: query (checkpoint {@code controllers.attn}), key, value, MLP. */
    private static final int DEBUG_LAYER = Integer.parseInt(System.getenv().getOrDefault("INKLING_DEBUG_ROUTE", "-1"));
    static final String[] GATES = {"attn", "k", "v", "mlp"};

    final InklingModelArgs args;
    final int index;
    final boolean local;
    final boolean looped;
    final boolean blockBoundary;
    final Device device;
    final InklingAttention attention;
    final InklingExperts experts;
    final Tensor attnQuery;        // [D] (local) or [3, D] (global); null for layer 0
    final Tensor mlpQuery;         // [D]
    final Tensor inputNorm;        // local layers
    final Tensor qNorm, kNorm, vNorm;   // global layers
    final Tensor postNorm;
    final Tensor attnConv, mlpConv; // [D, taps] float32
    final Tensor router, routerBias;
    final Tensor[][] gates;        // [4][down bf16, forget f32, gain f32]; null outside the loop

    InklingLayer(InklingModelArgs args, int index, Map<String, Tensor> t, Device device) {
        this.args = args;
        this.index = index;
        this.device = device;
        this.local = args.isLocal(index);
        this.looped = args.isLooped(index);
        this.blockBoundary = index % args.layersPerAttnResBlock() == 0;
        String p = "layers." + index + ".";
        attention = new InklingAttention(args, index, t, device);
        experts = new InklingExperts(args, index, t, device);
        attnQuery = index > 0 ? keep(f32(t.get("attn_res.attn_queries." + (index - 1)))) : null;
        mlpQuery = keep(f32(t.get("attn_res.mlp_queries." + index)));
        if (local) {
            inputNorm = keep(f32(t.get(p + "attn_norm.weight")));
            qNorm = kNorm = vNorm = null;
        } else {
            qNorm = keep(f32(t.get(p + "q_input_norm.weight")));
            kNorm = keep(f32(t.get(p + "k_input_norm.weight")));
            vNorm = keep(f32(t.get(p + "v_input_norm.weight")));
            inputNorm = null;
        }
        postNorm = keep(f32(t.get(p + "mlp_norm.weight")));
        attnConv = keep(f32(reshape(t.get(p + "attn_sconv.weight"), -1, args.sconvKernel())));
        mlpConv = keep(f32(reshape(t.get(p + "mlp_sconv.weight"), -1, args.sconvKernel())));
        router = keep(f32(t.get(p + "mlp.gate.weight")));
        routerBias = keep(f32(t.get(p + "mlp.gate.bias")));
        if (looped) {
            gates = new Tensor[4][];
            for (int g = 0; g < 4; g++) {
                String c = p + "controllers." + GATES[g] + ".";
                gates[g] = new Tensor[]{keep(t.get(c + "down_weight")), keep(f32(t.get(c + "forget_weight"))),
                        keep(f32(t.get(c + "gain_weight")))};
            }
        } else {
            gates = null;
        }
    }

    /** Names of the checkpoint tensors a layer reads (experts included). */
    static Set<String> names(InklingModelArgs args, int index) {
        Set<String> names = new HashSet<>();
        String p = "layers." + index + ".";
        for (String s : InklingAttention.names(args, index)) {
            names.add(s);
        }
        for (String s : InklingExperts.names(args, index)) {
            names.add(s);
        }
        if (index > 0) {
            names.add("attn_res.attn_queries." + (index - 1));
        }
        names.add("attn_res.mlp_queries." + index);
        if (args.isLocal(index)) {
            names.add(p + "attn_norm.weight");
        } else {
            names.add(p + "q_input_norm.weight");
            names.add(p + "k_input_norm.weight");
            names.add(p + "v_input_norm.weight");
        }
        for (String s : new String[]{"mlp_norm.weight", "attn_sconv.weight", "mlp_sconv.weight", "mlp.gate.weight", "mlp.gate.bias"}) {
            names.add(p + s);
        }
        if (args.isLooped(index)) {
            for (String c : GATES) {
                for (String s : new String[]{"down_weight", "forget_weight", "gain_weight"}) {
                    names.add(p + "controllers." + c + "." + s);
                }
            }
        }
        return names;
    }

    /**
     * Softmax-weighted read of the AttnRes states: {@code nq} learned queries attend over the completed blocks and
     * the current partial state (unweighted-RMS-normalized), scale {@code D^-0.5}.
     *
     * @param blocks  completed block states, each {@code [T, D]}.
     * @param partial the current state {@code [T, D]}.
     * @param query   {@code [nq, D]} or {@code [D]} float32.
     * @return float32 {@code [T, nq, D]}.
     */
    static Tensor attnRes(List<Tensor> blocks, Tensor partial, Tensor query) {
        Tensor[] states = new Tensor[blocks.size() + 1];
        for (int i = 0; i < blocks.size(); i++) {
            states[i] = f32(blocks.get(i));
        }
        states[blocks.size()] = f32(partial);
        Tensor stacked = unweightedRms(o("aten::stack", states, 1L), 1e-6);              // [T, n, D]
        long d = stacked.size(2);
        Tensor q = reshape(query, -1, d);                                                 // [nq, D]
        Tensor scores = mul(o("aten::matmul", stacked, o("aten::t", q)), 1.0 / Math.sqrt(d));   // [T, n, nq]
        Tensor weights = o("aten::softmax.int", scores, 1L, null);
        return o("aten::matmul", transpose(weights, 1, 2), stacked);                      // [T, nq, D]
    }

    /** {@code (1 - tanh(forget(g))) * h + tanh(gain(g)) * message}, {@code g = down([rms(h), rms(message)])}. */
    private Tensor gate(int which, Tensor hidden, Tensor message) {
        Tensor[] w = gates[which];
        Tensor hf = f32(hidden);
        Tensor mf = f32(message);
        double eps = args.normEps();
        Tensor g = cat(-1, unweightedRms(hf, eps), unweightedRms(mf, eps));
        g = f32(linear(bf16(g), w[0]));
        Tensor forget = tanh(linear(g, w[1]));
        Tensor gain = tanh(linear(g, w[2]));
        return add(mul(o("aten::rsub.Scalar", forget, 1.0), hf), mul(gain, mf));
    }

    /**
     * Routing: sigmoid scores, selection by score plus bias, weights renormalized and scaled.
     *
     * @return {@code {indices [T, topk] int64 on the host, weights [T, topk] float32 on the host}}.
     */
    private Object[] route(Tensor x) {
        Tensor logits = linear(f32(x), router);
        Tensor scores = sigmoid(logits);
        Tensor idx = Aten.call("aten::topk", add(scores, routerBias), (long) args.numExpertsPerTok(), -1L, true, false)[1];
        if (DEBUG_LAYER == index) {
            Tensor top = Aten.call("aten::topk", add(scores, routerBias), 10L, -1L, true, true)[0].to(Device.CPU());
            float[] tv = top.floatArray();
            for (int t = 0; t < top.size(0); t++) {
                System.out.printf("route layer %d token %d: 8th %.5f 9th %.5f 10th %.5f margin %.5f%n", index, t,
                        tv[t * 10 + 7], tv[t * 10 + 8], tv[t * 10 + 9], tv[t * 10 + 7] - tv[t * 10 + 8]);
            }
        }
        Tensor w = o("aten::gather", scores, 1L, idx);
        w = o("aten::div.Tensor", w, o("aten::add.Scalar", o("aten::sum.dim_IntList", w, new long[]{-1}, true), 1e-20));
        w = mul(w, args.routeScale());
        return new Object[]{idx.to(Device.CPU()).longArray(), w.to(Device.CPU()).floatArray()};
    }

    /** Routing on the device: {@code {indices [T, topk] int64, weights [T, topk] float32}}. */
    private Tensor[] routeOnDevice(Tensor x) {
        Tensor logits = linear(f32(x), router);
        Tensor scores = sigmoid(logits);
        Tensor idx = Aten.call("aten::topk", add(scores, routerBias), (long) args.numExpertsPerTok(), -1L, true, false)[1];
        Tensor w = o("aten::gather", scores, 1L, idx);
        w = o("aten::div.Tensor", w, o("aten::add.Scalar", o("aten::sum.dim_IntList", w, new long[]{-1}, true), 1e-20));
        return new Tensor[]{idx, mul(w, args.routeScale())};
    }

    /**
     * Runs the layer for {@code T} tokens.
     *
     * @param h        layer input, bf16 {@code [T, D]}.
     * @param blocks   completed AttnRes block states before this layer.
     * @param messages float32 {@code [T, 4, D]} from the previous pass (query, key, value, MLP), or {@code null}.
     * @param st       sequence state.
     * @param app      layer application (selects caches).
     * @param start    position of the first token.
     * @return the layer output, bf16 {@code [T, D]}.
     */
    Tensor forward(Tensor h, List<Tensor> blocks, Tensor messages, InklingState st, int app, int start) {
        double eps = args.normEps();
        Tensor qIn;
        Tensor kIn;
        Tensor vIn;
        Tensor msgQ = messages == null ? null : o("aten::select.int", messages, 1L, 0L);
        Tensor msgK = messages == null ? null : o("aten::select.int", messages, 1L, 1L);
        Tensor msgV = messages == null ? null : o("aten::select.int", messages, 1L, 2L);
        Tensor msgMlp = messages == null ? null : o("aten::select.int", messages, 1L, 3L);
        if (local) {
            Tensor a = index == 0 ? h : o("aten::select.int", attnRes(blocks, h, attnQuery), 1L, 0L);
            if (messages != null) {
                qIn = bf16(rms(gate(0, a, msgQ), inputNorm, eps));
                kIn = bf16(rms(gate(1, a, msgK), inputNorm, eps));
                vIn = bf16(rms(gate(2, a, msgV), inputNorm, eps));
            } else {
                qIn = kIn = vIn = bf16(rms(a, inputNorm, eps));
            }
        } else {
            Tensor all = attnRes(blocks, h, attnQuery);                                  // [T, 3, D]
            Tensor aq = o("aten::select.int", all, 1L, 0L);
            Tensor ak = o("aten::select.int", all, 1L, 1L);
            Tensor av = o("aten::select.int", all, 1L, 2L);
            if (messages != null) {
                aq = gate(0, aq, msgQ);
                ak = gate(1, ak, msgK);
                av = gate(2, av, msgV);
            }
            qIn = bf16(rms(aq, qNorm, eps));
            kIn = bf16(rms(ak, kNorm, eps));
            vIn = bf16(rms(av, vNorm, eps));
        }

        InklingDebug.tap("qin", app, qIn);
        Tensor attn = attention.forward(qIn, kIn, vIn, st, app, start);
        InklingDebug.tap("attn", app, attn);
        attn = InklingConv.apply(attn, attnConv, st.conv[app][InklingState.CONV_ATTN]);
        Tensor residual;
        List<Tensor> mlpBlocks = blocks;
        if (blockBoundary) {
            residual = f32(attn);
            mlpBlocks = new ArrayList<>(blocks);
            mlpBlocks.add(h);
        } else {
            residual = add(f32(h), f32(attn));
        }
        Tensor m = o("aten::select.int", attnRes(mlpBlocks, residual, mlpQuery), 1L, 0L);
        if (messages != null) {
            m = gate(3, m, msgMlp);
        }
        InklingDebug.tap("mlpin", app, m);
        Tensor x = bf16(rms(m, postNorm, eps));
        Tensor moe;
        if (experts.marlin()) {
            Tensor[] r = routeOnDevice(x);
            moe = bf16(experts.forwardMarlin(x, r[0], r[1]));
        } else {
            Object[] r = route(x);
            moe = bf16(experts.forward(x, (long[]) r[0], (float[]) r[1]));
        }
        InklingDebug.tap("moe", app, moe);
        Tensor mlp = InklingConv.apply(moe, mlpConv, st.conv[app][InklingState.CONV_MLP]);
        return bf16(add(residual, f32(mlp)));
    }
}
