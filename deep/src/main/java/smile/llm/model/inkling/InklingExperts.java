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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.util.AutoScope;

import static smile.llm.model.inkling.Ix.*;

/**
 * The routed experts of one layer, kept on the device in their checkpoint format (NVFP4 or MXFP8) and dequantized to
 * bf16 one expert at a time when a token selects it. This is the reference path (correct, not fast): it follows the
 * Hugging Face model, which dequantizes the whole checkpoint to bf16 on load.
 *
 * <p>The checkpoint's {@code w13} rows alternate gate and up ({@code gate0, up0, gate1, up1, ...}); an expert's
 * gate-up weight is de-interleaved into {@code [gate; up]} halves.
 *
 * @author Haifeng Li
 */
final class InklingExperts {
    /** E2M1 code to value. */
    private static final float[] E2M1 = {0f, 0.5f, 1f, 1.5f, 2f, 3f, 4f, 6f, -0f, -0.5f, -1f, -1.5f, -2f, -3f, -4f, -6f};

    private final InklingModelArgs args;
    private final InklingModelArgs.ExpertFormat format;
    private final Device device;
    private final Tensor w13;
    private final Tensor w13Scale;
    private final Tensor w2;
    private final Tensor w2Scale;
    private final float[] w13Factor;      // amax / outer_scale_denominator per expert (NVFP4)
    private final float[] w2Factor;
    private final Tensor table;
    /** Marlin-packed weights and scales (null: the reference path dequantizes per expert). */
    private Tensor q13, s13, g13, q2, s2, g2;
    private static final boolean MARLIN = !"dequant".equals(System.getenv("INKLING_EXPERTS"));

    InklingExperts(InklingModelArgs args, int layer, Map<String, Tensor> t, Device device) {
        this.args = args;
        this.format = args.expertFormat(layer);
        this.device = device;
        String p = "layers." + layer + ".mlp.experts.";
        this.w13 = keep(t.get(p + "w13_weight"));
        this.w13Scale = keep(t.get(p + "w13_weight_scale"));
        this.w2 = keep(t.get(p + "w2_weight"));
        this.w2Scale = keep(t.get(p + "w2_weight_scale"));
        if (format == InklingModelArgs.ExpertFormat.NVFP4) {
            this.w13Factor = factor(t.get(p + "w13_weight_amax"), t.get(p + "w13_weight_outer_scale_denominator"));
            this.w2Factor = factor(t.get(p + "w2_weight_amax"), t.get(p + "w2_weight_outer_scale_denominator"));
        } else if (format == InklingModelArgs.ExpertFormat.MXFP8) {
            this.w13Factor = null;
            this.w2Factor = null;
        } else {
            throw new IllegalArgumentException("unsupported expert format " + format);
        }
        this.table = keep(Tensor.of(E2M1, 16).to(device));
        if (MARLIN && device.isCUDA() && smile.torch.Native.marlinMoeNvfp4Available()
                && smile.torch.Native.marlinMoeFp8Available()) {
            repack();
        }
    }

    /** Whether the Marlin kernels run the experts. */
    boolean marlin() {
        return q13 != null;
    }

    /** Repacks the checkpoint tensors into the Marlin layouts and drops the originals. */
    private void repack() {
        if (format == InklingModelArgs.ExpertFormat.NVFP4) {
            q13 = keep(smile.torch.Native.marlinNvfp4RepackWeightStack(w13));
            q2 = keep(smile.torch.Native.marlinNvfp4RepackWeightStack(w2));
            // weight = fp4 * scale * factor; the kernel's global scale is the inverse
            Tensor[] a = smile.torch.Native.marlinNvfp4RepackScalesStack(w13Scale, inverse(w13Factor));
            Tensor[] b = smile.torch.Native.marlinNvfp4RepackScalesStack(w2Scale, inverse(w2Factor));
            s13 = keep(a[0]);
            g13 = keep(a[1]);
            s2 = keep(b[0]);
            g2 = keep(b[1]);
        } else {
            q13 = keep(smile.torch.Native.marlinFp8RepackWeightStack(w13));
            q2 = keep(smile.torch.Native.marlinFp8RepackWeightStack(w2));
            s13 = keep(smile.torch.Native.marlinFp8RepackScalesStack(w13.dtype() == ScalarType.UInt8 ? w13Scale : w13Scale));
            s2 = keep(smile.torch.Native.marlinFp8RepackScalesStack(w2Scale));
        }
        w13.close();
        w13Scale.close();
        w2.close();
        w2Scale.close();
    }

    private Tensor inverse(float[] factor) {
        float[] inv = new float[factor.length];
        for (int i = 0; i < inv.length; i++) {
            inv[i] = 1f / factor[i];
        }
        return Tensor.of(inv, inv.length).to(device);
    }

    static Iterable<String> names(InklingModelArgs args, int layer) {
        List<String> names = new ArrayList<>();
        String p = "layers." + layer + ".mlp.experts.";
        for (String w : new String[]{"w13", "w2"}) {
            names.add(p + w + "_weight");
            names.add(p + w + "_weight_scale");
            if (args.expertFormat(layer) == InklingModelArgs.ExpertFormat.NVFP4) {
                names.add(p + w + "_weight_amax");
                names.add(p + w + "_weight_outer_scale_denominator");
            }
        }
        return names;
    }

    private static float[] factor(Tensor amax, Tensor denominator) {
        float[] a = amax.to(Device.CPU()).floatArray();
        float d = denominator.to(Device.CPU()).floatArray()[0];
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] / d;
        }
        return out;
    }

    /** Dequantizes one expert's matrix {@code [N, K]} to bf16. */
    private Tensor dequant(Tensor packed, Tensor scale, float factor) {
        long n = packed.size(0);
        if (format == InklingModelArgs.ExpertFormat.NVFP4) {
            long k = packed.size(1) * 2;
            Tensor lo = o("aten::bitwise_and.Scalar", packed, 15L);
            Tensor hi = o("aten::bitwise_right_shift.Tensor_Scalar", packed, 4L);
            Tensor codes = reshape(o("aten::stack", new Tensor[]{lo, hi}, -1L), -1).to(ScalarType.Int64);
            Tensor vals = reshape(o("aten::index_select", table, 0L, codes), n, k / 16, 16);
            vals = mul(vals, unsqueeze(f32(scale), -1));
            return reshape(mul(vals, factor), n, k).to(ScalarType.BFloat16);
        }
        long k = packed.size(1);
        Tensor vals = reshape(f32(packed), n, k / 32, 32);
        Tensor s = o("aten::exp2", o("aten::add.Scalar", f32(scale), -127.0));
        return reshape(mul(vals, unsqueeze(s, -1)), n, k).to(ScalarType.BFloat16);
    }

    /** Marlin MoE block size: small enough to limit padding. */
    private int blockSize(int tokens) {
        int block = 8;
        for (int candidate : new int[]{8, 16, 32, 48, 64}) {
            block = candidate;
            if ((double) tokens * args.numExpertsPerTok() / args.numExperts() / candidate < 0.9) {
                break;
            }
        }
        return block;
    }

    /** The softcapped SwiGLU on interleaved gate/up columns {@code [M, 2 * inter]}; bf16 {@code [M, inter]}. */
    private Tensor activate(Tensor gateUp) {
        long inter = args.intermediateSize();
        Tensor pair = reshape(f32(gateUp), gateUp.size(0), inter, 2);
        Tensor gate = o("aten::select.int", pair, 2L, 0L);
        Tensor up = o("aten::select.int", pair, 2L, 1L);
        return contiguous(mul(mul(mul(tanh(mul(gate, 1.0 / args.gateSoftcap())), args.gateSoftcap()), sigmoid(gate)),
                mul(tanh(mul(up, 1.0 / args.upSoftcap())), args.upSoftcap())).to(ScalarType.BFloat16));
    }

    /**
     * The Marlin path: grouped GEMMs over all selected experts; the gate-up product keeps the checkpoint's
     * interleaved rows (the activation reads even and odd columns).
     *
     * @param x       bf16 {@code [T, D]}.
     * @param indices int64 {@code [T, topk]} on the device.
     * @param weights float32 {@code [T, topk]} on the device.
     * @return float32 {@code [T, D]}.
     */
    Tensor forwardMarlin(Tensor x, Tensor indices, Tensor weights) {
        int topk = args.numExpertsPerTok();
        int tokens = (int) x.size(0);
        int block = blockSize(tokens);
        Tensor align = smile.torch.Native.moeAlignBlockSize(indices, 0, args.numExperts(), block);
        Tensor w = contiguous(weights);
        boolean fp4 = format == InklingModelArgs.ExpertFormat.NVFP4;
        Tensor gateUp = fp4
                ? smile.torch.Native.marlinMoeNvfp4Gemm(x, q13, s13, g13, align, w, block, topk, false)
                : smile.torch.Native.marlinMoeFp8Gemm(x, q13, s13, align, w, block, topk, false);
        Tensor act = activate(gateUp);
        Tensor down = fp4
                ? smile.torch.Native.marlinMoeNvfp4Gemm(act, q2, s2, g2, align, w, block, 1, true)
                : smile.torch.Native.marlinMoeFp8Gemm(act, q2, s2, align, w, block, 1, true);
        return o("aten::sum.dim_IntList", f32(reshape(down, tokens, topk, -1)), new long[]{1}, false);
    }

    /**
     * {@code sum_k weight_k * down_k(act(gate_up_k(x)))} over each token's selected experts, accumulated in float32.
     *
     * @param x       bf16 {@code [T, D]}.
     * @param indices selected experts, {@code T * topk} values, row major.
     * @param weights routing weights, same layout.
     * @return float32 {@code [T, D]}.
     */
    Tensor forward(Tensor x, long[] indices, float[] weights) {
        int topk = args.numExpertsPerTok();
        long tokens = x.size(0);
        long inter = args.intermediateSize();
        Map<Long, List<int[]>> byExpert = new TreeMap<>();
        for (int i = 0; i < indices.length; i++) {
            byExpert.computeIfAbsent(indices[i], key -> new ArrayList<>()).add(new int[]{i / topk, i});
        }
        Tensor y = zeros(device, ScalarType.Float, tokens, x.size(1));
        for (var e : byExpert.entrySet()) {
            int expert = (int) (long) e.getKey();
            List<int[]> rows = e.getValue();
            long[] sel = new long[rows.size()];
            float[] we = new float[rows.size()];
            for (int i = 0; i < sel.length; i++) {
                sel[i] = rows.get(i)[0];
                we[i] = weights[rows.get(i)[1]];
            }
            AutoScope scope = new AutoScope();
            Tensor.push(scope);
            try {
                Tensor rowIdx = Tensor.of(sel, sel.length).to(device);
                Tensor gateUp = dequant(o("aten::select.int", w13, 0L, (long) expert),
                        o("aten::select.int", w13Scale, 0L, (long) expert), w13Factor == null ? 1f : w13Factor[expert]);
                // rows alternate gate/up: [inter, 2, D] -> [gate; up]
                Tensor pair = reshape(gateUp, inter, 2, -1);
                Tensor gu = cat(0, o("aten::select.int", pair, 1L, 0L), o("aten::select.int", pair, 1L, 1L));
                Tensor down = dequant(o("aten::select.int", w2, 0L, (long) expert),
                        o("aten::select.int", w2Scale, 0L, (long) expert), w2Factor == null ? 1f : w2Factor[expert]);
                Tensor xs = o("aten::index_select", x, 0L, rowIdx);
                Tensor h = linear(xs, gu);
                Tensor gate = f32(narrow(h, 1, 0, inter));
                Tensor up = f32(narrow(h, 1, inter, inter));
                Tensor act = mul(mul(mul(tanh(mul(gate, 1.0 / args.gateSoftcap())), args.gateSoftcap()), sigmoid(gate)),
                        mul(tanh(mul(up, 1.0 / args.upSoftcap())), args.upSoftcap())).to(ScalarType.BFloat16);
                Tensor out = mul(linear(act, down), Tensor.of(we, sel.length, 1).to(device));
                Aten.call("aten::index_add_", y, 0L, rowIdx, f32(out));
            } finally {
                Tensor.pop();
            }
        }
        return y;
    }
}
