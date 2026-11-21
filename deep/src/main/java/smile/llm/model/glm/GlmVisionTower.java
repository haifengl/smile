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

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;

import static smile.llm.model.glm.Gx.*;

/**
 * The GLM-5.3-Flash vision encoder (a port of {@code Glm5NextVisionModel}): a patch embedding, 24 pre-norm blocks (RMS norm,
 * attention with per-head RMS-normalized queries and keys and an axial 2D rotary embedding over the whole head, a clamped
 * SwiGLU MLP, all with biases), a final RMS norm, a 2x2 convolution that merges four patches into one token, and the
 * patch merger (projection, LayerNorm, GELU, clamped SwiGLU) that maps to the language model's hidden size.
 *
 * <p>The attention of each image is independent (images are attended separately, as the reference's cumulative sequence
 * lengths do). Weights are replicated on every rank; no collectives are used. Create and run it on the rank's own thread.
 *
 * @author Haifeng Li
 */
final class GlmVisionTower {
    private record Block(Tensor norm1, Tensor norm2, Tensor qkvW, Tensor qkvB, Tensor projW, Tensor projB, Tensor qNorm,
                         Tensor kNorm, Tensor gateW, Tensor gateB, Tensor upW, Tensor upB, Tensor downW, Tensor downB) { }

    /** Compute dtype: float32 by default (the encoder is small and its bf16 rounding shows in the features), {@code -Dsmile.glm.vision.bf16=true} for bf16. */
    private static final boolean BF16 = Boolean.getBoolean("smile.glm.vision.bf16");

    private final GlmVisionArgs a;
    private final ScalarType dtype;
    private Tensor patchW;       // [hidden, C * P * P]: the Conv3d weight summed over the (repeated) frames
    private Tensor patchB;
    private final List<Block> blocks = new ArrayList<>();
    private Tensor postNorm;
    private Tensor mergeW;       // [out, hidden * merge * merge]: the Conv2d weight, flattened (channel, row, column)
    private Tensor mergeB;
    private Tensor projW;
    private Tensor lnW;
    private Tensor lnB;
    private Tensor gateW;
    private Tensor upW;
    private Tensor downW;

    GlmVisionTower(GlmVisionArgs args, DeepSeekCheckpoint ck) {
        this.a = args;
        ScalarType saved = weightType();
        this.dtype = BF16 || saved == ScalarType.Float ? saved : ScalarType.Float;
        setThreadWeightType(dtype);
        try {
            load(args, ck);
        } finally {
            setThreadWeightType(saved);
        }
    }

    private void load(GlmVisionArgs args, DeepSeekCheckpoint ck) {
        String p = "model.visual.";
        this.patchW = scoped(() -> {
            Tensor w = f32(ck.get(p + "patch_embed.proj.weight").to(device()));          // [hidden, C, T, P, P]
            Tensor summed = o("aten::sum.dim_IntList", w, new long[]{2}, false);
            return o("aten::reshape", summed, new long[]{args.hiddenSize(), args.patchDim()}).to(weightType());
        });
        this.patchB = vector(ck, p + "patch_embed.proj.bias");
        for (int i = 0; i < args.depth(); i++) {
            String b = p + "blocks." + i + ".";
            blocks.add(new Block(vector(ck, b + "norm1.weight"), vector(ck, b + "norm2.weight"), weight(ck, b + "attn.qkv"),
                    vector(ck, b + "attn.qkv.bias"), weight(ck, b + "attn.proj"), vector(ck, b + "attn.proj.bias"),
                    vector(ck, b + "attn.q_norm.weight"), vector(ck, b + "attn.k_norm.weight"), weight(ck, b + "mlp.gate_proj"),
                    vector(ck, b + "mlp.gate_proj.bias"), weight(ck, b + "mlp.up_proj"), vector(ck, b + "mlp.up_proj.bias"),
                    weight(ck, b + "mlp.down_proj"), vector(ck, b + "mlp.down_proj.bias")));
        }
        this.postNorm = vector(ck, p + "post_layernorm.weight");
        int m = args.mergeSize();
        this.mergeW = scoped(() -> o("aten::reshape", f32(ck.get(p + "downsample.weight").to(device())),
                new long[]{args.outHiddenSize(), (long) args.hiddenSize() * m * m}).to(weightType()));
        this.mergeB = vector(ck, p + "downsample.bias");
        this.projW = weight(ck, p + "merger.proj");
        this.lnW = vector(ck, p + "merger.post_projection_norm.weight");
        this.lnB = vector(ck, p + "merger.post_projection_norm.bias");
        this.gateW = weight(ck, p + "merger.gate_proj");
        this.upW = weight(ck, p + "merger.up_proj");
        this.downW = weight(ck, p + "merger.down_proj");
    }

    /** cos and sin of the axial rotary embedding of each patch, {@code [N, headDim]} float32 on the device. */
    private Tensor[] rotary(int[][] grids, long n) {
        int d = a.headDim();
        int half = d / 4;                                    // frequencies per axis
        double[] inv = new double[half];
        for (int i = 0; i < half; i++) {
            inv[i] = 1.0 / Math.pow(a.ropeTheta(), (2.0 * i) / (d / 2));
        }
        float[] cos = new float[(int) (n * d)];
        float[] sin = new float[(int) (n * d)];
        int m = a.mergeSize();
        long row = 0;
        for (int[] g : grids) {
            int gh = g[1];
            int gw = g[2];
            for (int t = 0; t < g[0]; t++) {
                for (int bh = 0; bh < gh / m; bh++) {
                    for (int bw = 0; bw < gw / m; bw++) {
                        for (int mh = 0; mh < m; mh++) {
                            for (int mw = 0; mw < m; mw++) {
                                double h = bh * m + mh;
                                double w = bw * m + mw;
                                int base = (int) (row * d);
                                for (int i = 0; i < half; i++) {
                                    float ch = (float) Math.cos(h * inv[i]);
                                    float sh = (float) Math.sin(h * inv[i]);
                                    float cw = (float) Math.cos(w * inv[i]);
                                    float sw = (float) Math.sin(w * inv[i]);
                                    // [h freqs, w freqs, h freqs, w freqs]
                                    cos[base + i] = ch;
                                    cos[base + half + i] = cw;
                                    cos[base + 2 * half + i] = ch;
                                    cos[base + 3 * half + i] = cw;
                                    sin[base + i] = sh;
                                    sin[base + half + i] = sw;
                                    sin[base + 2 * half + i] = sh;
                                    sin[base + 3 * half + i] = sw;
                                }
                                row++;
                            }
                        }
                    }
                }
            }
        }
        return new Tensor[]{keep(Tensor.of(cos, n, d).to(device())), keep(Tensor.of(sin, n, d).to(device()))};
    }

    private static Tensor rotateHalf(Tensor x) {
        long h = x.size(-1) / 2;
        return cat(-1, o("aten::neg", narrow(x, -1, h, h)), narrow(x, -1, 0, h));
    }

    private Tensor sdpa(Tensor q, Tensor k, Tensor v) {
        MemorySegment out = smile.torch.smile_torch_h.smile_torch_scaled_dot_product_attention(q.handle(), k.handle(), v.handle(),
                MemorySegment.NULL, 0.0, 0, 1, Math.pow(a.headDim(), -0.5));
        return new Tensor(out);
    }

    private Tensor block(Block b, Tensor x, Tensor cos, Tensor sin, long[] cu) {
        long n = x.size(0);
        int heads = a.numHeads();
        int hd = a.headDim();
        Tensor h = rms(x, b.norm1(), a.rmsNormEps());
        Tensor qkv = o("aten::reshape", add(linear(h, b.qkvW()), b.qkvB()), new long[]{n, 3, heads, hd});
        Tensor q = rms(o("aten::select.int", qkv, 1L, 0L), b.qNorm(), a.rmsNormEps());
        Tensor k = rms(o("aten::select.int", qkv, 1L, 1L), b.kNorm(), a.rmsNormEps());
        Tensor v = o("aten::select.int", qkv, 1L, 2L);
        Tensor c = unsqueeze(cos, 1);
        Tensor s = unsqueeze(sin, 1);
        q = add(mul(q, c), mul(rotateHalf(q), s));
        k = add(mul(k, c), mul(rotateHalf(k), s));
        // [N, heads, hd] -> [1, heads, N, hd] in the weight dtype (bf16: flash attention) per image; images never attend to each other
        ScalarType at = dtype;
        Tensor qb = o("aten::permute", q, new long[]{1, 0, 2}).to(at);
        Tensor kb = o("aten::permute", k, new long[]{1, 0, 2}).to(at);
        Tensor vb = o("aten::permute", v, new long[]{1, 0, 2}).to(at);
        List<Tensor> outs = new ArrayList<>();
        for (int i = 0; i + 1 < cu.length; i++) {
            long len = cu[i + 1] - cu[i];
            Tensor qs = o("aten::contiguous", unsqueeze(narrow(qb, 1, cu[i], len), 0));
            Tensor ks = o("aten::contiguous", unsqueeze(narrow(kb, 1, cu[i], len), 0));
            Tensor vs = o("aten::contiguous", unsqueeze(narrow(vb, 1, cu[i], len), 0));
            Tensor o1 = sdpa(qs, ks, vs);                                                 // [1, heads, len, hd]
            outs.add(o("aten::reshape", o("aten::permute", o("aten::squeeze.dim", o1, 0L), new long[]{1, 0, 2}),
                    new long[]{len, (long) heads * hd}).to(ScalarType.Float));
        }
        Tensor attn = outs.size() == 1 ? outs.get(0) : cat(0, outs.toArray(Tensor[]::new));
        x = add(x, add(linear(attn, b.projW()), b.projB()));
        Tensor y = rms(x, b.norm2(), a.rmsNormEps());
        double limit = a.swigluLimit();
        Tensor gate = o("aten::clamp", add(linear(y, b.gateW()), b.gateB()), null, limit);
        Tensor up = o("aten::clamp", add(linear(y, b.upW()), b.upB()), -limit, limit);
        return add(x, add(linear(mul(o("aten::silu", gate), up), b.downW()), b.downB()));
    }

    /**
     * Encodes the images.
     *
     * @param pixels patch rows {@code [N, C * P * P]} of all images back to back (any device; float32).
     * @param grids  one {@code (t, h, w)} grid in patches per image.
     * @return the features {@code [N / merge^2, outHidden]} float32 on the device, in prompt order.
     */
    Tensor forward(Tensor pixels, int[][] grids) {
        long n = pixels.size(0);
        long[] cu = new long[grids.length + 1];
        for (int i = 0; i < grids.length; i++) {
            cu[i + 1] = cu[i] + (long) grids[i][0] * grids[i][1] * grids[i][2];
        }
        if (cu[grids.length] != n) {
            throw new IllegalArgumentException("the grids cover " + cu[grids.length] + " patches, the matrix has " + n);
        }
        Tensor[] rope = rotary(grids, n);
        Tensor x = keep(add(linear(pixels.to(device()), patchW), patchB));
        for (Block b : blocks) {
            Tensor in = x;
            Tensor next = layerScoped(() -> block(b, in, rope[0], rope[1], cu));
            if (in != null) {
                in.close();
            }
            x = keep(next);
        }
        rope[0].close();
        rope[1].close();
        int m = a.mergeSize();
        Tensor in = x;
        Tensor out = layerScoped(() -> {
            Tensor normed = rms(in, postNorm, a.rmsNormEps());
            // consecutive groups of merge^2 patches are one merge block: (row, column, channel) -> (channel, row, column)
            Tensor grouped = o("aten::reshape", normed, new long[]{-1L, m, m, a.hiddenSize()});
            Tensor flat = o("aten::reshape", o("aten::permute", grouped, new long[]{0, 3, 1, 2}),
                    new long[]{-1L, (long) a.hiddenSize() * m * m});
            Tensor merged = add(linear(flat, mergeW), mergeB);                                    // the 2x2 convolution
            Tensor ln = o("aten::layer_norm", linear(merged, projW), new long[]{a.outHiddenSize()}, lnW, lnB, 1e-5, true);
            // exact GELU: x * (1 + erf(x / sqrt 2)) / 2
            Tensor y = mul(o("aten::mul.Scalar", ln, 0.5), o("aten::add.Scalar", o("aten::erf", o("aten::mul.Scalar", ln, Math.sqrt(0.5))), 1.0));
            double limit = a.swigluLimit();
            Tensor gate = o("aten::clamp", linear(y, gateW), null, limit);
            Tensor up = o("aten::clamp", linear(y, upW), -limit, limit);
            return linear(mul(o("aten::silu", gate), up), downW);
        });
        in.close();
        return out;
    }
}
