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
package smile.llm.model.qwen;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import smile.deep.activation.GELU;
import smile.deep.layer.EmbeddingLayer;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.attention.AttentionBackends;
import smile.llm.attention.AttentionContext;
import smile.torch.Native;
import smile.util.AutoScope;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_list_as_module;
import static smile.torch.smile_torch_h.smile_module_list_create;
import static smile.torch.smile_torch_h.smile_module_list_free;
import static smile.torch.smile_torch_h.smile_module_list_push_back;
import static smile.torch.smile_torch_h.smile_module_register_module;

/**
 * Qwen3.8 / Qwen3.5 vision tower ({@code model.visual.*}).
 *
 * <p>Patch embed is a bias linear equivalent to Conv3d
 * {@code (temporal, patch, patch)} over flattened patches. DeepStack is
 * rejected when indexes are non-empty.
 *
 * @author Haifeng Li
 */
public class QwenVisionTower extends LayerBlock {
    private final QwenVisionArgs args;
    private final LinearLayer patchEmbed;
    private final EmbeddingLayer posEmbed;
    private final List<VisionBlock> blocks;
    private final VisionMerger merger;
    private final int numGridPerSide;

    /**
     * @param args vision hyperparameters.
     */
    public QwenVisionTower(QwenVisionArgs args) {
        if (args.hasDeepStack()) {
            throw new IllegalArgumentException(
                    "DeepStack is not implemented (Qwen3.8 uses empty deepstack_visual_indexes)");
        }
        this.args = args;
        this.numGridPerSide = (int) Math.round(Math.sqrt(args.numPositionEmbeddings()));
        this.patchEmbed = new LinearLayer(args.patchDim(), args.hiddenSize(), true);
        this.posEmbed = new EmbeddingLayer(args.numPositionEmbeddings(), args.hiddenSize());
        this.blocks = new ArrayList<>(args.depth());
        MemorySegment moduleList = smile_module_list_create();
        for (int i = 0; i < args.depth(); i++) {
            VisionBlock block = new VisionBlock(args);
            blocks.add(block);
            smile_module_list_push_back(moduleList, block.module());
        }
        this.merger = new VisionMerger(args);

        add("patch_embed", namedChild("patch_embed", "proj", patchEmbed.module()));
        add("pos_embed", posEmbed);
        MemorySegment listAsModule = smile_module_list_as_module(moduleList);
        add("blocks", listAsModule);
        smile_module_free(listAsModule);
        smile_module_list_free(moduleList);
        add("merger", merger);
    }

    private static MemorySegment namedChild(String parentName, String childName, MemorySegment child) {
        MemorySegment parent;
        try (Arena arena = Arena.ofConfined()) {
            parent = check(smile_module_create(arena.allocateFrom(parentName)));
            smile_module_register_module(parent, arena.allocateFrom(childName), child);
        }
        return parent;
    }

    /** @return vision hyperparameters. */
    public QwenVisionArgs args() {
        return args;
    }

    @Override
    public Tensor forward(Tensor input) {
        throw new UnsupportedOperationException(
                "Use forward(pixelValues, gridThw) for the vision tower");
    }

    /**
     * Encodes packed patches to LLM vision tokens.
     *
     * @param pixelValues {@code [N, patchDim]}.
     * @param gridThw     {@code [numMedia][3]} = (T,H,W) patch units.
     * @return {@code [N/merge^2, outHidden]} (caller owns).
     */
    public Tensor forward(Tensor pixelValues, int[][] gridThw) {
        if (pixelValues == null || gridThw == null || gridThw.length == 0) {
            throw new IllegalArgumentException("pixelValues and gridThw required");
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try (VisionRoPE rope = computeVisionRoPE(gridThw, pixelValues.device())) {
            Tensor h = patchEmbed.forward(pixelValues);
            Tensor pos = interpolatePosEmbed(gridThw, h.device(), h.dtype());
            Tensor x = h.add(pos);
            int[] cu = buildCuSeqlens(gridThw);
            for (VisionBlock block : blocks) {
                Tensor next = block.forward(x, cu, rope.cos(), rope.sin());
                if (next != x) {
                    x.close();
                }
                x = next;
            }
            Tensor merged = merger.forward(x);
            merged.promoteToParent();
            return merged;
        } finally {
            Tensor.pop();
        }
    }

    private static int[] buildCuSeqlens(int[][] gridThw) {
        List<Integer> lengths = new ArrayList<>();
        for (int[] g : gridThw) {
            int hw = g[1] * g[2];
            for (int t = 0; t < g[0]; t++) {
                lengths.add(hw);
            }
        }
        int[] cu = new int[lengths.size() + 1];
        for (int i = 0; i < lengths.size(); i++) {
            cu[i + 1] = cu[i] + lengths.get(i);
        }
        return cu;
    }

    private Tensor interpolatePosEmbed(int[][] gridThw, Device device, ScalarType dtype) {
        InterpTables tables = buildInterpTables(gridThw);
        Tensor acc = null;
        try {
            for (int c = 0; c < 4; c++) {
                int[] col = new int[tables.numTokens];
                for (int i = 0; i < tables.numTokens; i++) {
                    col[i] = tables.indices[i * 4 + c];
                }
                float[] wCol = new float[tables.numTokens];
                for (int i = 0; i < tables.numTokens; i++) {
                    wCol[i] = tables.weights[i * 4 + c];
                }
                try (Tensor ids = Tensor.of(col).to(device);
                     Tensor emb = posEmbed.forward(ids);
                     Tensor w = Tensor.of(wCol).to(device).to(dtype).unsqueeze(-1);
                     Tensor weighted = emb.to(dtype).mul(w)) {
                    if (acc == null) {
                        acc = weighted.copy();
                    } else {
                        Tensor sum = acc.add(weighted);
                        acc.close();
                        acc = sum;
                    }
                }
            }
            acc.promoteToParent();
            return acc;
        } catch (RuntimeException e) {
            if (acc != null) {
                acc.close();
            }
            throw e;
        }
    }

    private InterpTables buildInterpTables(int[][] gridThw) {
        List<Integer> indices = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        int m = args.spatialMergeSize();
        for (int[] g : gridThw) {
            int t = g[0];
            int h = g[1];
            int w = g[2];
            for (int tt = 0; tt < t; tt++) {
                for (int hb = 0; hb < h / m; hb++) {
                    for (int wb = 0; wb < w / m; wb++) {
                        for (int hi = 0; hi < m; hi++) {
                            for (int wi = 0; wi < m; wi++) {
                                bilinearCorners(hb * m + hi, wb * m + wi, h, w, indices, weights);
                            }
                        }
                    }
                }
            }
        }
        int n = indices.size() / 4;
        int[] idxArr = indices.stream().mapToInt(Integer::intValue).toArray();
        float[] wArr = new float[weights.size()];
        for (int i = 0; i < weights.size(); i++) {
            wArr[i] = weights.get(i);
        }
        return new InterpTables(n, idxArr, wArr);
    }

    private void bilinearCorners(int y, int x, int h, int w,
                                 List<Integer> indices, List<Float> weights) {
        double gy = h == 1 ? 0.0 : (double) y / (h - 1) * (numGridPerSide - 1);
        double gx = w == 1 ? 0.0 : (double) x / (w - 1) * (numGridPerSide - 1);
        int y0 = (int) Math.floor(gy);
        int x0 = (int) Math.floor(gx);
        int y1 = Math.min(y0 + 1, numGridPerSide - 1);
        int x1 = Math.min(x0 + 1, numGridPerSide - 1);
        y0 = Math.max(0, Math.min(y0, numGridPerSide - 1));
        x0 = Math.max(0, Math.min(x0, numGridPerSide - 1));
        double wy = gy - y0;
        double wx = gx - x0;
        indices.add(y0 * numGridPerSide + x0);
        indices.add(y0 * numGridPerSide + x1);
        indices.add(y1 * numGridPerSide + x0);
        indices.add(y1 * numGridPerSide + x1);
        weights.add((float) ((1 - wy) * (1 - wx)));
        weights.add((float) ((1 - wy) * wx));
        weights.add((float) (wy * (1 - wx)));
        weights.add((float) (wy * wx));
    }

    private VisionRoPE computeVisionRoPE(int[][] gridThw, Device device) {
        int rotaryDim = args.headDim();
        int half = rotaryDim / 2;
        int freqDim = Math.max(1, half / 2);
        List<Integer> posIds = new ArrayList<>();
        int m = args.spatialMergeSize();
        for (int[] g : gridThw) {
            int t = g[0];
            int h = g[1];
            int w = g[2];
            for (int tt = 0; tt < t; tt++) {
                for (int hb = 0; hb < h / m; hb++) {
                    for (int wb = 0; wb < w / m; wb++) {
                        for (int hi = 0; hi < m; hi++) {
                            for (int wi = 0; wi < m; wi++) {
                                posIds.add(hb * m + hi);
                                posIds.add(wb * m + wi);
                            }
                        }
                    }
                }
            }
        }
        int seq = posIds.size() / 2;
        float[] invFreq = new float[freqDim];
        for (int i = 0; i < freqDim; i++) {
            invFreq[i] = (float) Math.exp(-Math.log(10000.0) * (2.0 * i) / half);
        }
        float[] cosData = new float[seq * rotaryDim];
        float[] sinData = new float[seq * rotaryDim];
        for (int s = 0; s < seq; s++) {
            int hid = posIds.get(s * 2);
            int wid = posIds.get(s * 2 + 1);
            float[] freq = new float[half];
            for (int i = 0; i < freqDim; i++) {
                freq[i] = hid * invFreq[i];
                freq[freqDim + i] = wid * invFreq[i];
            }
            // pad if half odd relative to 2*freqDim
            for (int i = 2 * freqDim; i < half; i++) {
                freq[i] = 0f;
            }
            for (int i = 0; i < half; i++) {
                float c = (float) Math.cos(freq[i]);
                float sn = (float) Math.sin(freq[i]);
                cosData[s * rotaryDim + i] = c;
                cosData[s * rotaryDim + half + i] = c;
                sinData[s * rotaryDim + i] = sn;
                sinData[s * rotaryDim + half + i] = sn;
            }
        }
        Tensor cos = Tensor.of(cosData, seq, rotaryDim).to(device);
        Tensor sin = Tensor.of(sinData, seq, rotaryDim).to(device);
        cos.detachFromScopes();
        sin.detachFromScopes();
        return new VisionRoPE(cos, sin);
    }

    private record InterpTables(int numTokens, int[] indices, float[] weights) {}

    private record VisionRoPE(Tensor cos, Tensor sin) implements AutoCloseable {
        @Override
        public void close() {
            cos.close();
            sin.close();
        }
    }

    /** ViT block. */
    static final class VisionBlock extends LayerBlock {
        private final VisionLayerNorm norm1;
        private final VisionLayerNorm norm2;
        private final LinearLayer qkv;
        private final LinearLayer proj;
        private final LinearLayer fc1;
        private final LinearLayer fc2;
        private final GELU gelu = new GELU(false);
        private final int numHeads;
        private final int headDim;
        private final double scale;

        VisionBlock(QwenVisionArgs args) {
            this.numHeads = args.numHeads();
            this.headDim = args.headDim();
            this.scale = 1.0 / Math.sqrt(headDim);
            this.norm1 = new VisionLayerNorm(args.hiddenSize(), 1e-6);
            this.norm2 = new VisionLayerNorm(args.hiddenSize(), 1e-6);
            this.qkv = new LinearLayer(args.hiddenSize(), args.hiddenSize() * 3, true);
            this.proj = new LinearLayer(args.hiddenSize(), args.hiddenSize(), true);
            this.fc1 = new LinearLayer(args.hiddenSize(), args.intermediateSize(), true);
            this.fc2 = new LinearLayer(args.intermediateSize(), args.hiddenSize(), true);
            add("norm1", norm1);
            add("attn", namedChild("attn", qkv, proj));
            add("norm2", norm2);
            add("mlp", namedMlp(fc1, fc2));
        }

        @Override
        public Tensor forward(Tensor input) {
            throw new UnsupportedOperationException("Use forward(hidden, cuSeqlens, cos, sin)");
        }

        private static MemorySegment namedChild(String name, LinearLayer qkv, LinearLayer proj) {
            MemorySegment parent;
            try (Arena arena = Arena.ofConfined()) {
                parent = check(smile_module_create(arena.allocateFrom(name)));
                smile_module_register_module(parent, arena.allocateFrom("qkv"), qkv.module());
                smile_module_register_module(parent, arena.allocateFrom("proj"), proj.module());
            }
            return parent;
        }

        private static MemorySegment namedMlp(LinearLayer fc1, LinearLayer fc2) {
            MemorySegment parent;
            try (Arena arena = Arena.ofConfined()) {
                parent = check(smile_module_create(arena.allocateFrom("mlp")));
                smile_module_register_module(parent, arena.allocateFrom("linear_fc1"), fc1.module());
                smile_module_register_module(parent, arena.allocateFrom("linear_fc2"), fc2.module());
            }
            return parent;
        }

        Tensor forward(Tensor hidden, int[] cuSeqlens, Tensor cos, Tensor sin) {
            try (Tensor n1 = norm1.forward(hidden);
                 Tensor attnOut = attention(n1, cuSeqlens, cos, sin);
                 Tensor h1 = hidden.add(attnOut);
                 Tensor n2 = norm2.forward(h1);
                 Tensor mlp1 = fc1.forward(n2);
                 Tensor act = gelu.forward(mlp1);
                 Tensor mlp2 = fc2.forward(act);
                 Tensor out = h1.add(mlp2)) {
                Tensor copy = out.copy();
                copy.promoteToParent();
                return copy;
            }
        }

        private Tensor attention(Tensor hidden, int[] cu, Tensor cos, Tensor sin) {
            long seq = hidden.shape()[0];
            try (Tensor qkvOut = qkv.forward(hidden);
                 Tensor r = qkvOut.reshape(seq, 3L * numHeads, headDim);
                 var qSlice = Index.slice(0, numHeads);
                 var kSlice = Index.slice(numHeads, 2 * numHeads);
                 var vSlice = Index.slice(2 * numHeads, 3 * numHeads);
                 Tensor qRaw = r.get(Index.Colon, qSlice, Index.Colon);
                 Tensor kRaw = r.get(Index.Colon, kSlice, Index.Colon);
                 Tensor vRaw = r.get(Index.Colon, vSlice, Index.Colon);
                 Tensor q = applyVisionRoPE(qRaw, cos, sin);
                 Tensor k = applyVisionRoPE(kRaw, cos, sin);
                 Tensor attn = AttentionBackends.kernel().forward(
                         q, k, vRaw, null,
                         AttentionContext.ragged(scale, false, numHeads, numHeads, headDim, cu));
                 Tensor projected = proj.forward(attn.reshape(seq, (long) numHeads * headDim))) {
                Tensor copy = projected.copy();
                copy.promoteToParent();
                return copy;
            }
        }

        private static Tensor applyVisionRoPE(Tensor x, Tensor cos, Tensor sin) {
            try (Tensor xF = x.to(ScalarType.Float);
                 Tensor c = cos.to(ScalarType.Float).unsqueeze(1);
                 Tensor s = sin.to(ScalarType.Float).unsqueeze(1);
                 Tensor rotated = PartialRotaryEncoding.rotateHalf(xF);
                 Tensor t1 = xF.mul(c);
                 Tensor t2 = rotated.mul(s);
                 Tensor emb = t1.add(t2)) {
                Tensor out = emb.to(x.dtype());
                out.promoteToParent();
                return out;
            }
        }
    }

    /** Spatial merger LN → pack → MLP. */
    static final class VisionMerger extends LayerBlock {
        private final VisionLayerNorm norm;
        private final LinearLayer fc1;
        private final LinearLayer fc2;
        private final GELU gelu = new GELU(false);
        private final int hidden;
        private final int pack;

        VisionMerger(QwenVisionArgs args) {
            int m = args.spatialMergeSize();
            this.pack = m * m;
            this.hidden = args.hiddenSize() * pack;
            this.norm = new VisionLayerNorm(args.hiddenSize(), 1e-6);
            this.fc1 = new LinearLayer(hidden, hidden, true);
            this.fc2 = new LinearLayer(hidden, args.outHiddenSize(), true);
            add("norm", norm);
            add("linear_fc1", fc1);
            add("linear_fc2", fc2);
        }

        @Override
        public Tensor forward(Tensor x) {
            long n = x.shape()[0];
            if (n % pack != 0) {
                throw new IllegalArgumentException(
                        "vision tokens " + n + " not divisible by merge^2=" + pack);
            }
            try (Tensor normalized = norm.forward(x);
                 Tensor packed = normalized.reshape(n / pack, hidden);
                 Tensor h1 = fc1.forward(packed);
                 Tensor act = gelu.forward(h1);
                 Tensor out = fc2.forward(act)) {
                Tensor copy = out.copy();
                copy.promoteToParent();
                return copy;
            }
        }
    }
}
