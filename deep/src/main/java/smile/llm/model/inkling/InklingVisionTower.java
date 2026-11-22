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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static smile.llm.model.inkling.Ix.*;

/**
 * The Inkling-2 vision tower: a ViT over 20x20 pixel patches (bf16) with layer scale, a class token per image and
 * axial 2D rotary embeddings, learned position embeddings resampled (bilinear) from a 32x32 grid, per-image full attention
 * and a two-layer projector that merges each 2x2 block of patches into one language-model embedding.
 *
 * @author Haifeng Li
 */
public final class InklingVisionTower {
    private final InklingVisionArgs args;
    private final Device device;
    private final Tensor patchW, posTable, cls, preW, preB, projIn, projOut, invFreq;
    private final Tensor[][] layers;     // per layer: ln1 w,b, ln2 w,b, ls1, ls2, fc1 w,b, fc2 w,b, q w,b, k w,b, v w,b, out w,b

    private static final String P = "model.visual.";

    private InklingVisionTower(InklingVisionArgs args, Device device, Map<String, Tensor> t) {
        this.args = args;
        this.device = device;
        patchW = keep(t.get(P + "backbone.embeddings.patch_embedding.weight"));
        posTable = keep(t.get(P + "backbone.embeddings.position_embedding"));
        cls = keep(add(t.get(P + "backbone.embeddings.class_embedding"), t.get(P + "backbone.embeddings.cls_position_embedding")));
        preW = keep(t.get(P + "backbone.ln_pre.weight"));
        preB = keep(t.get(P + "backbone.ln_pre.bias"));
        projIn = keep(t.get(P + "proj_in.weight"));
        projOut = keep(t.get(P + "proj_out.weight"));
        int spatial = args.hidden() / args.heads() / 2;
        float[] inv = new float[spatial / 2];
        for (int i = 0; i < inv.length; i++) {
            inv[i] = (float) (1.0 / Math.pow(args.ropeTheta(), (2.0 * i) / spatial));
        }
        invFreq = keep(Tensor.of(inv, inv.length).to(device));
        layers = new Tensor[args.layers()][];
        String[] names = {"ln_1.weight", "ln_1.bias", "ln_2.weight", "ln_2.bias", "ls_1.gamma", "ls_2.gamma", "mlp.c_fc.weight",
                "mlp.c_fc.bias", "mlp.c_proj.weight", "mlp.c_proj.bias", "self_attn.q_proj.weight", "self_attn.q_proj.bias",
                "self_attn.k_proj.weight", "self_attn.k_proj.bias", "self_attn.v_proj.weight", "self_attn.v_proj.bias",
                "self_attn.out_proj.weight", "self_attn.out_proj.bias"};
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new Tensor[names.length];
            for (int j = 0; j < names.length; j++) {
                layers[i][j] = keep(t.get(P + "backbone.layers." + i + "." + names[j]));
            }
        }
    }

    /**
     * Loads the tower from a checkpoint.
     *
     * @param ck     the checkpoint reader.
     * @param args   vision arguments.
     * @param device the device.
     * @return the tower.
     * @throws IOException if a tensor is missing.
     */
    static InklingVisionTower load(InklingCheckpoint ck, InklingVisionArgs args, Device device) throws IOException {
        Set<String> names = new HashSet<>();
        for (String s : new String[]{"backbone.embeddings.patch_embedding.weight", "backbone.embeddings.position_embedding",
                "backbone.embeddings.class_embedding", "backbone.embeddings.cls_position_embedding", "backbone.ln_pre.weight",
                "backbone.ln_pre.bias", "proj_in.weight", "proj_out.weight"}) {
            names.add(P + s);
        }
        for (int i = 0; i < args.layers(); i++) {
            for (String s : new String[]{"ln_1.weight", "ln_1.bias", "ln_2.weight", "ln_2.bias", "ls_1.gamma", "ls_2.gamma",
                    "mlp.c_fc.weight", "mlp.c_fc.bias", "mlp.c_proj.weight", "mlp.c_proj.bias", "self_attn.q_proj.weight",
                    "self_attn.q_proj.bias", "self_attn.k_proj.weight", "self_attn.k_proj.bias", "self_attn.v_proj.weight",
                    "self_attn.v_proj.bias", "self_attn.out_proj.weight", "self_attn.out_proj.bias"}) {
                names.add(P + "backbone.layers." + i + "." + s);
            }
        }
        return new InklingVisionTower(args, device, ck.readRaw(device, names));
    }

    /** Per-patch gather indices and weights of the bilinear resampling of the position table, in merge-block order. */
    private float[][] interpolation(int[][] grids, int total) {
        int side = args.posGrid(), merge = args.merge();
        float[] weights = new float[total * 4];
        float[] indices = new float[total * 4];
        int at = 0;
        for (int[] g : grids) {
            int h = g[1] * merge, w = g[2] * merge;                       // the patch grid
            int blocksW = w / merge;
            for (int i = 0; i < g[0] * h * w; i++) {
                int within = i % (h * w);
                int inCol = within % merge, inRow = within / merge % merge;
                int blockCol = within / (merge * merge) % blocksW, blockRow = within / (merge * merge * blocksW);
                int row = blockRow * merge + inRow, col = blockCol * merge + inCol;
                float srcH = (row + 0.5f) * side / h - 0.5f, srcW = (col + 0.5f) * side / w - 0.5f;
                int fh = (int) Math.floor(srcH), fw = (int) Math.floor(srcW);
                for (int a = 0; a < 2; a++) {
                    for (int b = 0; b < 2; b++) {
                        float wh = Math.max(0f, 1f - Math.abs(srcH - fh - a));
                        float ww = Math.max(0f, 1f - Math.abs(srcW - fw - b));
                        int th = Math.min(side - 1, Math.max(0, fh + a)), tw = Math.min(side - 1, Math.max(0, fw + b));
                        indices[at * 4 + a * 2 + b] = th * side + tw;
                        weights[at * 4 + a * 2 + b] = wh * ww;
                    }
                }
                at++;
            }
        }
        return new float[][]{indices, weights};
    }

    private Tensor layerNorm(Tensor x, Tensor w, Tensor b) {
        return o("aten::layer_norm", x, new long[]{args.hidden()}, w, b, args.eps(), true);
    }

    private static Tensor biased(Tensor x, Tensor w, Tensor b) {
        return o("aten::linear", x, w, b);
    }

    /**
     * Encodes images.
     *
     * @param pixels tiles {@code [N, tile, tile, 3]} float32 (CPU or device) of all images back to back.
     * @param grids  one {@code (t, rows, cols)} per image (counted in tiles).
     * @return bf16 embeddings {@code [N, text hidden]}, one per tile in order.
     */
    public Tensor encode(Tensor pixels, int[][] grids) {
        int merge = args.merge(), patch = args.patch(), hidden = args.hidden();
        int tokens = (int) pixels.size(0);
        int patches = tokens * merge * merge;
        Tensor pv = bf16(pixels.to(device));
        Tensor flat = reshape(o("aten::transpose.int", reshape(pv, tokens * 1L, merge, patch, merge, patch, 3), 2L, 3L),
                -1, 3L * patch * patch);
        Tensor emb = linear(flat, patchW);                                         // [patches, hidden]

        float[][] interp = interpolation(grids, patches);
        Tensor idx = Tensor.of(interp[0], patches, 4).to(device).to(ScalarType.Int64);
        Tensor wts = Tensor.of(interp[1], patches, 4, 1).to(device);
        Tensor gathered = reshape(o("aten::index_select", posTable, 0L, reshape(idx, -1)), patches, 4, hidden);
        Tensor pos = o("aten::sum.dim_IntList", mul(gathered, wts), new long[]{1}, false);       // float32
        emb = add(emb, pos.to(ScalarType.BFloat16));

        // sequence: [class token, patches...] per image
        int images = grids.length;
        int total = patches + images;
        long[] patchIndex = new long[patches];
        int[] seqStart = new int[images + 1];
        int p = 0;
        float[] posIds = new float[total * 2];
        int seq = 0;
        for (int im = 0; im < images; im++) {
            int[] g = grids[im];
            int h = g[1] * merge, w = g[2] * merge;
            seqStart[im] = seq;
            seq += 1;                                           // the class token sits at (0, 0)
            int blocksW = w / merge;
            for (int i = 0; i < h * w; i++) {
                int inCol = i % merge, inRow = i / merge % merge;
                int blockCol = i / (merge * merge) % blocksW, blockRow = i / (merge * merge * blocksW);
                patchIndex[p] = seq;
                posIds[seq * 2] = blockRow * merge + inRow + 1;
                posIds[seq * 2 + 1] = blockCol * merge + inCol + 1;
                p++;
                seq++;
            }
        }
        seqStart[images] = seq;
        Tensor pIdx = Tensor.of(patchIndex, patches).to(device);
        Tensor all = o("aten::repeat", unsqueeze(cls, 0), new long[]{total, 1});
        all = o("aten::index_copy", all, 0L, pIdx, emb);
        Tensor h = layerNorm(all, preW, preB);

        // axial rotary embedding: [cos, sin] of (width, height) frequencies, each repeated for an interleaved pair
        Tensor positions = Tensor.of(posIds, total, 2).to(device);
        Tensor freqs = mul(unsqueeze(positions, -1), invFreq);                          // [S, 2, 16]
        Tensor cosHW = o("aten::cos", freqs), sinHW = o("aten::sin", freqs);
        Tensor cos = o("aten::repeat_interleave.self_int", cat(-1, o("aten::select.int", cosHW, 1L, 1L), o("aten::select.int", cosHW, 1L, 0L)), 2L, -1L);
        Tensor sin = o("aten::repeat_interleave.self_int", cat(-1, o("aten::select.int", sinHW, 1L, 1L), o("aten::select.int", sinHW, 1L, 0L)), 2L, -1L);
        cos = unsqueeze(cos, 1);
        sin = unsqueeze(sin, 1);

        int heads = args.heads(), headDim = hidden / heads;
        for (Tensor[] L : layers) {
            Tensor x = layerNorm(h, L[0], L[1]);
            Tensor q = rope(reshape(biased(x, L[10], L[11]), total, heads, headDim), cos, sin);
            Tensor k = rope(reshape(biased(x, L[12], L[13]), total, heads, headDim), cos, sin);
            Tensor v = reshape(biased(x, L[14], L[15]), total, heads, headDim);
            Tensor[] parts = new Tensor[images];
            for (int im = 0; im < images; im++) {
                int from = seqStart[im], len = seqStart[im + 1] - from;
                Tensor qi = unsqueeze(transpose(narrow(q, 0, from, len), 0, 1), 0);
                Tensor ki = unsqueeze(transpose(narrow(k, 0, from, len), 0, 1), 0);
                Tensor vi = unsqueeze(transpose(narrow(v, 0, from, len), 0, 1), 0);
                Tensor a = o("aten::scaled_dot_product_attention", qi, ki, vi, null, 0.0, false, null);
                parts[im] = reshape(contiguous(transpose(o("aten::squeeze.dim", a, 0L), 0, 1)), len, hidden);
            }
            Tensor attn = images == 1 ? parts[0] : cat(0, parts);
            h = add(h, mul(L[4], biased(attn, L[16], L[17])));
            Tensor y = layerNorm(h, L[2], L[3]);
            y = biased(o("aten::gelu", biased(y, L[6], L[7]), "none"), L[8], L[9]);
            h = add(h, mul(L[5], y));
        }
        Tensor merged = reshape(o("aten::index_select", h, 0L, pIdx), -1, (long) merge * merge * hidden);
        return linear(o("aten::gelu", linear(merged, projIn), "none"), projOut);
    }

    /** {@code x * cos + rotate_pairwise(x) * sin} in float32, back to the input dtype. */
    private static Tensor rope(Tensor x, Tensor cos, Tensor sin) {
        Tensor xf = f32(x);
        long[] shape = xf.shape();
        Tensor pairs = reshape(xf, shape[0], shape[1], shape[2] / 2, 2);
        Tensor x1 = o("aten::select.int", pairs, 3L, 0L), x2 = o("aten::select.int", pairs, 3L, 1L);
        Tensor rotated = reshape(o("aten::stack", new Tensor[]{o("aten::neg", x2), x1}, 3L), shape[0], shape[1], shape[2]);
        return add(mul(xf, cos), mul(rotated, sin)).to(x.dtype());
    }
}
