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
package smile.llm.model.deepseek;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.util.AutoScope;

import static smile.llm.model.deepseek.Dsv.o;

/**
 * Top-k routed experts plus one shared expert. Routing scores are {@code sqrt(softplus(x W))}; a bias steers which
 * experts are picked but the weights come from the unbiased scores, renormalised and scaled. Each expert is a SwiGLU
 * FFN with clamped activations.
 *
 * <p>Validation implementation: experts are read from the checkpoint and dequantized on demand.
 *
 * @author Haifeng Li
 */
final class DeepSeekMoE {
    /** One SwiGLU FFN. */
    static final class Expert {
        private final DeepSeekLinear w1;
        private final DeepSeekLinear w3;
        private final DeepSeekLinear w2;
        private final double limit;

        Expert(DeepSeekCheckpoint ck, String prefix, double limit) {
            this.w1 = new DeepSeekLinear(ck, prefix + ".w1", null);
            this.w3 = new DeepSeekLinear(ck, prefix + ".w3", null);
            this.w2 = new DeepSeekLinear(ck, prefix + ".w2", null);
            this.limit = limit;
        }

        Tensor forward(Tensor x, Tensor weights) {
            ScalarType dtype = x.dtype();
            Tensor gate = DeepSeekOps.f32(w1.forward(x));
            Tensor up = DeepSeekOps.f32(w3.forward(x));
            if (limit > 0) {
                up = o("aten::clamp", up, -limit, limit);
                gate = o("aten::clamp", gate, null, limit);
            }
            Tensor h = o("aten::mul.Tensor", o("aten::silu", gate), up);
            if (weights != null) {
                h = o("aten::mul.Tensor", weights, h);
            }
            return w2.forward(h.to(dtype));
        }
    }

    private final DeepSeekModelArgs args;
    private final DeepSeekCheckpoint ck;
    private final String prefix;
    private final Tensor gateWeight;
    private final Tensor gateBias;
    private final Expert shared;
    private final int topk;
    private final int experts;
    /** Marlin kernel path on a CUDA device, or {@code null} for the dequantized reference path. */
    private DeepSeekMarlinExperts marlin;

    DeepSeekMoE(DeepSeekModelArgs args, DeepSeekCheckpoint ck, int layer) {
        this.args = args;
        this.ck = ck;
        this.prefix = "layers." + layer + ".ffn";
        this.gateWeight = DeepSeekOps.f32(ck.dev(prefix + ".gate.weight"));
        this.gateBias = DeepSeekOps.f32(ck.dev(prefix + ".gate.bias"));
        this.shared = new Expert(ck, prefix + ".shared_experts", args.swigluLimit());
        int[] cfg = args.moeConfig(layer);
        this.experts = cfg[0];
        this.topk = cfg[1];
        if (Dsv.marlin && Dsv.device().isCUDA()) {
            useMarlin(Dsv.device());
        }
    }

    /**
     * Runs the routed experts with the Marlin MoE kernel on a CUDA device.
     *
     * @param device the device holding the activations.
     */
    void useMarlin(smile.deep.tensor.Device device) {
        marlin = new DeepSeekMarlinExperts(ck, prefix, experts, topk, args.swigluLimit(), device, Dsv.ep());
    }

    /**
     * Routing: the chosen experts and their weights.
     *
     * @param flat tokens {@code [n, dim]}.
     * @return {@code {indices [n, topk] int64, weights [n, topk] float32}}.
     */
    Tensor[] route(Tensor flat) {
        Tensor scores = o("aten::sqrt", o("aten::softplus", o("aten::linear", DeepSeekOps.f32(flat), gateWeight)));
        Tensor indices = Aten.call("aten::topk", o("aten::add.Tensor", scores, gateBias), (long) topk, -1L, true, true)[1];
        Tensor weights = o("aten::gather", scores, 1L, indices);
        if (topk > 1) {
            weights = o("aten::div.Tensor", weights, o("aten::add.Scalar",
                    o("aten::sum.dim_IntList", weights, new long[]{-1}, true), 1e-20));
        }
        return new Tensor[]{indices, o("aten::mul.Scalar", weights, args.routeScale())};
    }

    /** Routed-expert output {@code [n, dim]} (float32) from dequantized experts read on demand. */
    Tensor routed(Tensor flat, Tensor indices, Tensor weights) {
        long n = flat.size(0);
        long[] idx = Dsv.host(indices).longArray();
        float[] w = Dsv.host(weights).floatArray();
        Map<Long, List<long[]>> byExpert = new TreeMap<>();     // expert -> [{row, weightIndex}]
        for (int i = 0; i < idx.length; i++) {
            byExpert.computeIfAbsent(idx[i], k -> new ArrayList<>()).add(new long[]{i / topk, i});
        }
        Tensor y = Dsv.zeros(ScalarType.Float, n, flat.size(1));
        for (var e : byExpert.entrySet()) {
            List<long[]> rows = e.getValue();
            long[] sel = new long[rows.size()];
            float[] we = new float[rows.size()];
            for (int i = 0; i < sel.length; i++) {
                sel[i] = rows.get(i)[0];
                we[i] = w[(int) rows.get(i)[1]];
            }
            // the dequantized expert weights are large: free them as soon as this expert is done
            AutoScope scope = new AutoScope();
            Tensor.push(scope);
            try {
                Tensor rowIdx = Dsv.dev(Tensor.of(sel, sel.length));
                Expert expert = new Expert(ck, prefix + ".experts." + e.getKey(), args.swigluLimit());
                Tensor out = expert.forward(o("aten::index_select", flat, 0L, rowIdx), Dsv.dev(Tensor.of(we, sel.length, 1)));
                Aten.call("aten::index_add_", y, 0L, rowIdx, DeepSeekOps.f32(out));
            } finally {
                Tensor.pop();
            }
        }
        return y;
    }

    Tensor forward(Tensor x) {
        long[] shape = x.shape();
        Tensor flat = x.reshape(-1, shape[shape.length - 1]);
        long n = flat.size(0);
        Tensor[] r = route(flat);
        Tensor y = marlin != null ? marlin.forward(flat, r[0], r[1]) : routed(flat, r[0], r[1]);
        y = o("aten::add.Tensor", y, DeepSeekOps.f32(shared.forward(flat, null)));
        return y.to(x.dtype()).reshape(shape);
    }
}
