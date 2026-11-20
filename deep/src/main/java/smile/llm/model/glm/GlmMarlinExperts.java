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

import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;
import smile.torch.Native;
import smile.util.AutoScope;

import static smile.llm.model.glm.Gx.*;

/**
 * The routed experts one rank owns, kept as FP8 (E4M3) with their bfloat16 group-128 scales in the Marlin layout and
 * multiplied by the Marlin MoE kernel: weight-only FP8 that needs no FP8 tensor cores, so it runs on A100. The
 * checkpoint's 128x128 block scales are expanded to one scale per 128 input channels and output column, which
 * is lossless. Per layer and rank the weights occupy {@code owned * 3 * dim * inter} bytes.
 *
 * @author Haifeng Li
 */
final class GlmMarlinExperts {
    private final int experts;
    private final int expertStart;
    private final int localExperts;
    private final int topk;
    private final int inter;
    private final double limit;
    private final int dim;
    private Tensor w13q;
    private Tensor w13s;
    private Tensor w2q;
    private Tensor w2s;

    /**
     * Loads and repacks the experts of a layer.
     *
     * @param ck      the checkpoint.
     * @param prefix  tensor name prefix up to {@code experts.}.
     * @param args    model arguments.
     * @param ep      the expert-parallel context, or {@code null} for all experts.
     */
    GlmMarlinExperts(DeepSeekCheckpoint ck, String prefix, GlmModelArgs args, Ep ep) {
        this.experts = args.numRoutedExperts();
        this.expertStart = ep == null ? 0 : (int) ((long) ep.rank() * experts / ep.world());
        int end = ep == null ? experts : (int) ((long) (ep.rank() + 1) * experts / ep.world());
        this.localExperts = end - expertStart;
        this.topk = args.numExpertsPerTok();
        this.inter = args.moeInterSize();
        this.dim = args.dim();
        this.limit = args.swigluLimit();
        Device device = device();
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            // raw FP8 staging of this rank's experts; gate rows first, then up rows, along the output dimension
            var f8 = new Tensor.Options().device(device).dtype(ScalarType.Float8e4m3fn);
            var f32 = new Tensor.Options().device(device).dtype(ScalarType.Float);
            int bs = 128;
            Tensor s13w = Tensor.empty(f8, localExperts, 2L * inter, dim);
            Tensor s13s = Tensor.empty(f32, localExperts, 2L * inter / bs, dim / bs);
            Tensor s2w = Tensor.empty(f8, localExperts, dim, inter);
            Tensor s2s = Tensor.empty(f32, localExperts, (long) dim / bs, inter / bs);
            for (int e = 0; e < localExperts; e++) {
                String p = prefix + (expertStart + e) + ".";
                copyInto(s13w, e, 0, ck.get(p + "gate_proj.weight").to(device));
                copyInto(s13w, e, inter, ck.get(p + "up_proj.weight").to(device));
                copyInto(s13s, e, 0, ck.get(p + "gate_proj.weight_scale_inv").to(device));
                copyInto(s13s, e, inter / bs, ck.get(p + "up_proj.weight_scale_inv").to(device));
                copyInto(s2w, e, 0, ck.get(p + "down_proj.weight").to(device));
                copyInto(s2s, e, 0, ck.get(p + "down_proj.weight_scale_inv").to(device));
            }
            this.w13q = keep(Native.marlinFp8RepackWeightStack(s13w));
            this.w13s = keep(Native.marlinFp8RepackBlockScalesStack(s13s));
            this.w2q = keep(Native.marlinFp8RepackWeightStack(s2w));
            this.w2s = keep(Native.marlinFp8RepackBlockScalesStack(s2s));
        } finally {
            Tensor.pop();
        }
    }

    /** {@code dst[expert, rowOffset: rowOffset + src.rows, :] = src}. */
    private static void copyInto(Tensor dst, int expert, long rowOffset, Tensor src) {
        Tensor slot = narrow(o("aten::select.int", dst, 0L, (long) expert), 0, rowOffset, src.size(0));
        Native.copy_(slot, src);
    }

    /** Marlin MoE block size: small enough to limit padding, as chosen by vLLM. */
    private int moeBlockSize(long tokens) {
        int block = 8;
        for (int candidate : new int[]{8, 16, 32, 48, 64}) {
            block = candidate;
            if ((double) tokens * topk / experts / candidate < 0.9) {
                break;
            }
        }
        return block;
    }

    /**
     * The partial routed output of this rank's experts.
     *
     * @param x       {@code [n, dim]} hidden rows (any float dtype).
     * @param indices {@code [n, topk]} int64 chosen experts (global ids).
     * @param weights {@code [n, topk]} float32 combine weights.
     * @return {@code [n, dim]} float32; assignments to experts of other ranks contribute zero.
     */
    Tensor forward(Tensor x, Tensor indices, Tensor weights) {
        long n = x.size(0);
        Tensor flat = o("aten::contiguous", x.to(ScalarType.BFloat16));
        Tensor ids = o("aten::contiguous", indices);
        Tensor w = o("aten::contiguous", weights.to(ScalarType.Float));
        int block = moeBlockSize(n);
        Tensor align = Native.moeAlignBlockSize(ids, expertStart, localExperts, block);
        Tensor gateUp = Native.marlinMoeFp8Gemm(flat, w13q, w13s, align, w, block, topk, false);
        Tensor gate = f32(narrow(gateUp, 1, 0, inter));
        Tensor up = f32(narrow(gateUp, 1, inter, inter));
        if (limit > 0) {
            gate = o("aten::clamp", gate, null, limit);
            up = o("aten::clamp", up, -limit, limit);
        }
        Tensor act = o("aten::contiguous", mul(o("aten::silu", gate), up).to(ScalarType.BFloat16));
        Tensor down = Native.marlinMoeFp8Gemm(act, w2q, w2s, align, w, block, 1, true);
        return sumDim(o("aten::reshape", f32(down), new long[]{n, topk, dim}), 1, false);
    }
}
