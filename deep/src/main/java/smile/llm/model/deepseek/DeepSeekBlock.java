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

import java.util.Map;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static smile.llm.model.deepseek.Dsv.o;

/**
 * A decoder block whose residual stream is {@code hc} parallel copies (Hyper-Connections). Attention and the FFN each
 * sit between {@code hcPre} (collapse the copies into one sublayer input) and {@code hcPost} (expand back and mix the
 * residual in through a doubly stochastic matrix). The mixing coefficients are derived from the stream itself, and the
 * ones a sublayer computes are used by the <em>next</em> one: attention uses what the previous layer's FFN produced,
 * the FFN what this attention produced.
 *
 * @author Haifeng Li
 */
final class DeepSeekBlock {
    final int layer;
    private final DeepSeekModelArgs args;
    private final DeepSeekAttention attention;
    private final DeepSeekMoE ffn;
    private final DeepSeekEngram engram;
    private final int engramSlot;
    private final Tensor attnNorm;
    private final Tensor ffnNorm;
    private final Tensor attnFn;
    private final Tensor ffnFn;
    private final Tensor attnBase;
    private final Tensor ffnBase;
    private final Tensor attnScale;
    private final Tensor ffnScale;

    DeepSeekBlock(DeepSeekModelArgs args, DeepSeekCheckpoint ck, int layer,
                  DeepSeekEngram.Rows engramRows) {
        String p = "layers." + layer;
        this.layer = layer;
        this.args = args;
        this.attention = new DeepSeekAttention(args, ck, layer);
        this.ffn = new DeepSeekMoE(args, ck, layer);
        this.engramSlot = args.engramSlot(layer);
        this.engram = engramSlot >= 0 ? new DeepSeekEngram(args, ck, layer, engramRows) : null;
        this.attnNorm = ck.dev(p + ".attn_norm.weight");
        this.ffnNorm = ck.dev(p + ".ffn_norm.weight");
        this.attnFn = DeepSeekOps.f32(ck.dev(p + ".hc_attn_fn"));
        this.ffnFn = DeepSeekOps.f32(ck.dev(p + ".hc_ffn_fn"));
        this.attnBase = DeepSeekOps.f32(ck.dev(p + ".hc_attn_base"));
        this.ffnBase = DeepSeekOps.f32(ck.dev(p + ".hc_ffn_base"));
        this.attnScale = DeepSeekOps.f32(ck.dev(p + ".hc_attn_scale"));
        this.ffnScale = DeepSeekOps.f32(ck.dev(p + ".hc_ffn_scale"));
    }

    DeepSeekAttention.State newState(DeepSeekAttention.Shared shared) {
        return attention.newState(shared);
    }

    void use(DeepSeekAttention.State s) {
        attention.use(s);
    }

    boolean hasEngram() {
        return engram != null;
    }

    Tensor applyEngram(Tensor h, Tensor hashIds) {
        // hashIds: [1, s, layers, cols] -> this layer's slice [1, s, cols]
        return engram.forward(h, o("aten::select.int", hashIds, 2L, (long) engramSlot));
    }

    private Tensor[] hcMixes(Tensor x, Tensor fn, Tensor scale, Tensor base) {
        long[] s = x.shape();
        Tensor flat = DeepSeekOps.f32(x).reshape(s[0], s[1], -1);
        Tensor r = o("aten::rsqrt", o("aten::add.Scalar",
                o("aten::mean.dim", o("aten::mul.Tensor", flat, flat), new long[]{-1}, true), args.normEps()));
        Tensor mixes = o("aten::mul.Tensor", o("aten::linear", flat, fn), r);
        return DeepSeekOps.hcSplitSinkhorn(mixes, scale, base, args.hcMult(), args.hcSinkhornIters(), args.hcEps());
    }

    /** Collapses the copies: {@code [1, s, hc, dim]} x {@code [1, s, hc]} to {@code [1, s, dim]}. */
    static Tensor hcPre(Tensor x, Tensor pre) {
        return o("aten::sum.dim_IntList", o("aten::mul.Tensor", pre.unsqueeze(-1), DeepSeekOps.f32(x)), new long[]{2})
                .to(x.dtype());
    }

    private static Tensor hcPost(Tensor x, Tensor residual, Tensor post, Tensor comb) {
        Tensor a = o("aten::mul.Tensor", post.unsqueeze(-1), x.unsqueeze(-2));
        Tensor b = o("aten::sum.dim_IntList", o("aten::mul.Tensor", comb.unsqueeze(-1), residual.unsqueeze(-2)), new long[]{2});
        return o("aten::add.Tensor", a, b).to(x.dtype());
    }

    /**
     * Runs the block.
     *
     * @param x        the stream {@code [1, s, hc, dim]}.
     * @param startPos absolute position of the first token.
     * @param preMix   attention's collapse weights, from the previous layer's FFN.
     * @param trace    optional map receiving intermediates.
     * @return {@code {stream, ffnPre}}.
     */
    Tensor[] forward(Tensor x, int startPos, Tensor preMix, Map<String, Tensor> trace) {
        return run(x, preMix, trace, h -> attention.forward(h, startPos));
    }

    /** As {@link #forward} for one decode token of each of several sequences (rows along the sequence axis). */
    Tensor[] forwardBatch(Tensor x, int[] pos, DeepSeekAttention.State[] states, Tensor preMix) {
        return run(x, preMix, null, h -> attention.forwardBatch(h, pos, states));
    }

    private Tensor[] run(Tensor x, Tensor preMix, Map<String, Tensor> trace,
                         java.util.function.UnaryOperator<Tensor> attend) {
        double eps = args.normEps();
        Tensor residual = x;
        Tensor[] attn = hcMixes(x, attnFn, attnScale, attnBase);
        Tensor h = DeepSeekOps.rmsNorm(hcPre(x, preMix), attnNorm, eps);
        h = attend.apply(h);
        if (trace != null) {
            trace.put("L" + layer + ".attn_out", h);
        }
        x = hcPost(h, residual, attn[1], attn[2]);

        residual = x;
        Tensor[] ff = hcMixes(x, ffnFn, ffnScale, ffnBase);
        h = DeepSeekOps.rmsNorm(hcPre(x, attn[0]), ffnNorm, eps);
        h = ffn.forward(h);
        if (trace != null) {
            trace.put("L" + layer + ".ffn_out", h);
        }
        x = hcPost(h, residual, ff[1], ff[2]);
        return new Tensor[]{x, ff[0]};
    }
}
