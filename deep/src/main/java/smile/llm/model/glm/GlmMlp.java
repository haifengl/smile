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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;
import smile.torch.Aten;
import smile.util.AutoScope;

import static smile.llm.model.glm.Gx.*;

/**
 * The feed-forward of GLM-5.3-Flash: a dense SwiGLU MLP (first layers) or the sigmoid-routed MoE with one shared
 * expert. All SwiGLU activations clamp {@code gate <= limit} and {@code |up| <= limit}. Reference path: routed
 * experts are dequantized from FP8 when used and freed right after.
 *
 * @author Haifeng Li
 */
final class GlmMlp {
    private final GlmModelArgs args;
    private final DeepSeekCheckpoint ck;
    private final String prefix;
    private final boolean moe;
    // dense MLP or shared expert
    private final Tensor wGate;
    private final Tensor wUp;
    private final Tensor wGateUp;       // gate|up as one weight (the fused path computes both with one GEMM)
    private final Tensor wDown;
    // router
    private final Tensor routerW;
    private final Tensor routerBias;

    /** Marlin FP8 kernels for the routed experts (CUDA, when the native library has them). */
    private GlmMarlinExperts marlin;

    GlmMlp(GlmModelArgs args, DeepSeekCheckpoint ck, int layer) {
        this.args = args;
        this.ck = ck;
        this.moe = args.isMoe(layer);
        this.prefix = args.layerPrefix(layer) + ".mlp.";
        String dense = moe ? prefix + "shared_experts." : prefix;
        // tensor parallelism: gate/up are column-parallel, down is row-parallel (its output is a partial sum)
        this.wGate = shard(ck, dense + "gate_proj", 0);
        this.wUp = shard(ck, dense + "up_proj", 0);
        this.wDown = shard(ck, dense + "down_proj", 1);
        this.wGateUp = device().isCUDA() && smile.torch.Native.glmSwigluAvailable() ? keep(cat(0, wGate, wUp)) : null;
        this.routerW = moe ? vector(ck, prefix + "gate.weight") : null;
        this.routerBias = moe ? vector(ck, prefix + "gate.e_score_correction_bias") : null;
        if (moe && device().isCUDA() && !"0".equals(System.getenv("GLM_MARLIN")) && smile.torch.Native.marlinMoeFp8Available()
                && smile.torch.Native.marlinFp8BlockScalesAvailable()) {
            this.marlin = new GlmMarlinExperts(ck, prefix + "experts.", args, ep());
        }
    }

    /** Experts kept on the device as raw FP8 (+ scales) once used: {@code {gate, gateScale, up, upScale, down, downScale}}. */
    private final Map<Integer, Tensor[]> resident = new java.util.HashMap<>();

    private Tensor[] expertFp8(int expert) {
        Tensor[] t = resident.get(expert);
        if (t != null) {
            return t;
        }
        String ep = prefix + "experts." + expert + ".";
        t = new Tensor[6];
        String[] names = {"gate_proj", "up_proj", "down_proj"};
        for (int i = 0; i < 3; i++) {
            t[2 * i] = ck.get(ep + names[i] + ".weight").to(device());
            t[2 * i + 1] = ck.get(ep + names[i] + ".weight_scale_inv").to(device());
        }
        if (device().isCUDA()) {
            for (Tensor x : t) {
                keep(x);
            }
            resident.put(expert, t);
        }
        return t;
    }

    /** {@code down(silu(min(gate, limit)) * clamp(up, -limit, limit))}. */
    private Tensor swiglu(Tensor x, Tensor gateW, Tensor upW, Tensor downW) {
        double limit = args.swigluLimit();
        if (wGateUp != null && gateW == wGate) {
            Tensor act = smile.torch.Native.glmSwiglu(linear(x, wGateUp), limit);
            if (act != null) {
                return linear(act, downW);
            }
        }
        Tensor gate = linear(x, gateW);
        Tensor up = linear(x, upW);
        if (limit > 0) {
            gate = o("aten::clamp", gate, null, limit);
            up = o("aten::clamp", up, -limit, limit);
        }
        return linear(mul(o("aten::silu", gate), up), downW);
    }

    /**
     * Routing: selection by sigmoid score plus bias, weights from the plain scores.
     *
     * @param flat {@code [n, dim]}.
     * @return {@code {indices [n, topk] int64, weights [n, topk] float32}}.
     */
    Tensor[] route(Tensor flat) {
        Tensor logits = linear(f32(flat), routerW);
        Tensor[] fused = smile.torch.Native.moeRoute(logits, routerBias, args.numExpertsPerTok(), args.routeScale());
        if (fused != null) {
            return fused;                       // int32 ids, float32 weights: sigmoid, bias-corrected top-k, normalise, scale
        }
        Tensor scores = sigmoid(logits);
        // a single expert group: the group-limited step selects the only group
        Tensor idx = Aten.call("aten::topk", add(scores, routerBias), (long) args.numExpertsPerTok(), -1L, true, true)[1];
        Tensor weights = o("aten::gather", scores, 1L, idx);
        weights = o("aten::div.Tensor", weights, o("aten::add.Scalar", sumDim(weights, -1, true), 1e-20));
        return new Tensor[]{idx, o("aten::mul.Scalar", weights, args.routeScale())};
    }

    Tensor forward(Tensor x, Map<String, Tensor> trace) {
        if (!moe) {
            return allReduce(swiglu(x, wGate, wUp, wDown));
        }
        Tensor[] r = route(x);
        if (trace != null) {
            trace.put("router.idx", keep(host(r[0].to(ScalarType.Int64))));
            trace.put("router.w", keep(host(r[1])));
        }
        if (marlin != null) {
            // the routed experts' partial sum and the shared expert's partial sum ride one all-reduce
            Tensor xb = x.dtype() == ScalarType.BFloat16 ? x : x.to(ScalarType.BFloat16);       // converted once for both
            Tensor shared = f32(swiglu(xb, wGate, wUp, wDown));
            return allReduce(marlin.forward(xb, r[0], r[1], shared));
        }
        long[] idx = host(r[0].to(ScalarType.Int64)).longArray();
        float[] w = host(r[1]).floatArray();
        int topk = args.numExpertsPerTok();
        Map<Long, List<long[]>> byExpert = new TreeMap<>();     // expert -> [{row, slot}]
        for (int i = 0; i < idx.length; i++) {
            byExpert.computeIfAbsent(idx[i], key -> new ArrayList<>()).add(new long[]{i / topk, i});
        }
        Tensor y = zeros(ScalarType.Float, x.size(0), x.size(1));
        double limit = args.swigluLimit();
        Ep ep = ep();
        for (var e : byExpert.entrySet()) {
            if (ep != null && !ep.owns((int) (long) e.getKey(), args.numRoutedExperts())) {
                continue;
            }
            List<long[]> rows = e.getValue();
            long[] sel = new long[rows.size()];
            float[] we = new float[rows.size()];
            for (int i = 0; i < sel.length; i++) {
                sel[i] = rows.get(i)[0];
                we[i] = w[(int) rows.get(i)[1]];
            }
            AutoScope scope = new AutoScope();
            Tensor.push(scope);
            try {
                Tensor rowIdx = Tensor.of(sel, sel.length).to(device());
                int expert = (int) (long) e.getKey();
                Tensor[] fp8 = expertFp8(expert);
                Tensor out = swiglu(o("aten::index_select", x, 0L, rowIdx),
                        dequantBlockFp8(fp8[0], fp8[1]).to(weightType()),
                        dequantBlockFp8(fp8[2], fp8[3]).to(weightType()),
                        dequantBlockFp8(fp8[4], fp8[5]).to(weightType()));
                out = mul(out, Tensor.of(we, sel.length, 1).to(device()));
                Aten.call("aten::index_add_", y, 0L, rowIdx, out);
            } finally {
                Tensor.pop();
            }
        }
        return allReduce(add(y, swiglu(x, wGate, wUp, wDown)));
    }
}
