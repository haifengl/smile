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
package smile.llm.model.minimax;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import smile.deep.activation.GELU;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.attention.AttentionBackends;
import smile.llm.attention.AttentionContext;
import smile.llm.model.qwen.VisionLayerNorm;
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
 * MiniMax-M3 vision encoder: a CLIP-style ViT (Conv3d patch embedding, pre-LayerNorm, 32 pre-norm blocks with a
 * partial 3D RoPE over t/h/w, no post-LayerNorm), followed by the projector MLP and the 2x2 patch-merge MLP that
 * map patches to language-model embeddings.
 *
 * <p>Parameter names match the checkpoint once the {@code vision_tower.} prefix is dropped
 * ({@code vision_model.encoder.layers.N.self_attn.q_proj.weight}, {@code multi_modal_projector.linear_1.bias}, ...),
 * so {@link #load} can hand tensors straight to {@code loadStateDict}.
 *
 * <p>Input is the processor's patch matrix: one row of {@code C * T * P * P} values per patch, patches ordered
 * by (time group, merge block row, merge block column, row in block, column in block). The Conv3d is applied as
 * the equivalent linear map.
 *
 * @author Haifeng Li
 */
public class MiniMaxVisionTower extends LayerBlock {
    private final MiniMaxVisionArgs args;
    private final LinearLayer patchEmbed;
    private final VisionLayerNorm preNorm;
    private final List<Block> blocks = new ArrayList<>();
    private final LinearLayer projector1;
    private final LinearLayer projector2;
    private final LinearLayer merge1;
    private final LinearLayer merge2;
    private final GELU gelu = new GELU(false);
    private final float[] invFreq;

    /**
     * Constructs the module graph (on CPU, float32).
     *
     * @param args hyperparameters.
     */
    public MiniMaxVisionTower(MiniMaxVisionArgs args) {
        this.args = args;
        int e = args.hiddenSize();
        this.patchEmbed = new LinearLayer(args.patchDim(), e, false);
        this.preNorm = new VisionLayerNorm(e, args.layerNormEps());
        MemorySegment list = smile_module_list_create();
        for (int i = 0; i < args.numLayers(); i++) {
            Block block = new Block(args);
            blocks.add(block);
            smile_module_list_push_back(list, block.module());
        }
        MemorySegment layers = smile_module_list_as_module(list);
        this.projector1 = new LinearLayer(e, args.projectorHidden(), true);
        this.projector2 = new LinearLayer(args.projectorHidden(), args.textHidden(), true);
        int mergeIn = args.textHidden() * args.mergeSize() * args.mergeSize();
        this.merge1 = new LinearLayer(mergeIn, args.projectorHidden(), true);
        this.merge2 = new LinearLayer(args.projectorHidden(), args.textHidden(), true);

        MemorySegment embeddings = group("embeddings", "patch_embedding", patchEmbed.module());
        MemorySegment encoder = group("encoder", "layers", layers);
        MemorySegment visionModel = group("vision_model", "embeddings", embeddings, "pre_layrnorm", preNorm.module(),
                "encoder", encoder);
        add("vision_model", visionModel);
        add("multi_modal_projector", group("multi_modal_projector", "linear_1", projector1.module(),
                "linear_2", projector2.module()));
        add("patch_merge_mlp", group("patch_merge_mlp", "linear_1", merge1.module(), "linear_2", merge2.module()));
        smile_module_free(layers);
        smile_module_list_free(list);

        int axis = args.axisDim();
        this.invFreq = new float[axis / 2];
        for (int i = 0; i < invFreq.length; i++) {
            invFreq[i] = (float) (1.0 / Math.pow(args.ropeTheta(), (2.0 * i) / axis));
        }
    }

    /** A plain module whose children are registered under the given names ({@code name, module, name, module...}). */
    private static MemorySegment group(String name, Object... children) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment parent = check(smile_module_create(arena.allocateFrom(name)));
            for (int i = 0; i < children.length; i += 2) {
                smile_module_register_module(parent, arena.allocateFrom((String) children[i]),
                        (MemorySegment) children[i + 1]);
            }
            return parent;
        }
    }

    /** @return the hyperparameters. */
    public MiniMaxVisionArgs args() {
        return args;
    }

    @Override
    public Tensor forward(Tensor input) {
        throw new UnsupportedOperationException("Use forward(pixelValues, gridThw)");
    }

    /**
     * Encodes patches into language-model embeddings.
     *
     * @param pixelValues {@code [N, patchDim]} patches in the compute dtype, on this module's device.
     * @param gridThw     one {@code (t, h, w)} patch grid per image or video, in patch units; the rows of
     *                    {@code pixelValues} are the grids' patches back to back.
     * @return {@code [N / merge^2, textHidden]} embeddings, one row per merged token (the caller owns it).
     */
    public Tensor forward(Tensor pixelValues, int[][] gridThw) {
        int[][] segments = segments(gridThw);
        long total = 0;
        for (int[] g : segments) {
            total += (long) g[0] * g[1] * g[2];
        }
        if (pixelValues.shape()[0] != total) {
            throw new IllegalArgumentException("pixelValues has " + pixelValues.shape()[0] + " rows but the grids need "
                    + total);
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Device device = pixelValues.device();
            ScalarType dtype = pixelValues.dtype();
            int[] cu = new int[segments.length + 1];
            for (int i = 0; i < segments.length; i++) {
                cu[i + 1] = cu[i] + segments[i][0] * segments[i][1] * segments[i][2];
            }
            Tensor[] cosSin = ropeTables(segments, (int) total, device);
            Tensor x = preNorm.forward(patchEmbed.forward(pixelValues));
            for (Block block : blocks) {
                x = block.forward(x, cu, cosSin[0], cosSin[1], args.rotaryDim());
            }
            Tensor y = projector2.forward(gelu.forward(projector1.forward(x)));
            y = y.reshape(y.shape()[0] / ((long) args.mergeSize() * args.mergeSize()), -1);
            Tensor out = merge2.forward(gelu.forward(merge1.forward(y)));
            if (out.dtype() != dtype) {
                out = out.to(dtype);
            }
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }

    /** Attention never spans more than {@code maxSegmentFrames} temporal groups: longer grids become several. */
    private int[][] segments(int[][] grids) {
        List<int[]> out = new ArrayList<>();
        int max = args.maxSegmentFrames();
        for (int[] g : grids) {
            if (g[0] <= max) {
                out.add(g);
            } else {
                for (int t = 0; t < g[0]; t += max) {
                    out.add(new int[]{Math.min(max, g[0] - t), g[1], g[2]});
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    /**
     * 3D RoPE tables {@code [N, 1, half]} (float32): half-dims {@code [0, a)} rotate by the frame index,
     * {@code [a, 2a)} by the row and {@code [2a, 3a)} by the column of the patch, where {@code a = axisDim / 2}.
     */
    private Tensor[] ropeTables(int[][] segments, int tokens, Device device) {
        int a = invFreq.length;
        int half = 3 * a;
        int m = args.mergeSize();
        float[] cos = new float[tokens * half];
        float[] sin = new float[tokens * half];
        int token = 0;
        for (int[] g : segments) {
            int h = g[1];
            int w = g[2];
            for (int t = 0; t < g[0]; t++) {
                for (int rem = 0; rem < h * w; rem++, token++) {
                    // Within a frame, patches are ordered by merge block (rows, columns), then inside the block.
                    int bh = rem / (m * w);
                    int r = rem % (m * w);
                    int bw = r / (m * m);
                    int r2 = r % (m * m);
                    int row = bh * m + r2 / m;
                    int col = bw * m + r2 % m;
                    for (int i = 0; i < a; i++) {
                        put(cos, sin, token * half + i, t * invFreq[i]);
                        put(cos, sin, token * half + a + i, row * invFreq[i]);
                        put(cos, sin, token * half + 2 * a + i, col * invFreq[i]);
                    }
                }
            }
        }
        Tensor c = Tensor.of(cos, tokens, 1, half).to(device);
        Tensor s = Tensor.of(sin, tokens, 1, half).to(device);
        return new Tensor[]{c, s};
    }

    private static void put(float[] cos, float[] sin, int at, float angle) {
        cos[at] = (float) Math.cos(angle);
        sin[at] = (float) Math.sin(angle);
    }

    /** One pre-norm transformer block. */
    private static final class Block extends LayerBlock {
        private final VisionLayerNorm norm1;
        private final VisionLayerNorm norm2;
        private final LinearLayer q;
        private final LinearLayer k;
        private final LinearLayer v;
        private final LinearLayer out;
        private final LinearLayer fc1;
        private final LinearLayer fc2;
        private final GELU gelu = new GELU(false);
        private final int heads;
        private final int headDim;
        private final double scale;

        Block(MiniMaxVisionArgs args) {
            int e = args.hiddenSize();
            this.heads = args.numHeads();
            this.headDim = args.headDim();
            this.scale = 1.0 / Math.sqrt(headDim);
            this.norm1 = new VisionLayerNorm(e, args.layerNormEps());
            this.norm2 = new VisionLayerNorm(e, args.layerNormEps());
            this.q = new LinearLayer(e, e, true);
            this.k = new LinearLayer(e, e, true);
            this.v = new LinearLayer(e, e, true);
            this.out = new LinearLayer(e, e, true);
            this.fc1 = new LinearLayer(e, args.intermediateSize(), true);
            this.fc2 = new LinearLayer(args.intermediateSize(), e, true);
            add("layer_norm1", norm1);
            add("layer_norm2", norm2);
            add("self_attn", group("self_attn", "q_proj", q.module(), "k_proj", k.module(), "v_proj", v.module(),
                    "out_proj", out.module()));
            add("mlp", group("mlp", "fc1", fc1.module(), "fc2", fc2.module()));
        }

        @Override
        public Tensor forward(Tensor input) {
            throw new UnsupportedOperationException();
        }

        Tensor forward(Tensor x, int[] cu, Tensor cos, Tensor sin, int rotaryDim) {
            long n = x.shape()[0];
            Tensor h = norm1.forward(x);
            Tensor qq = rope(q.forward(h).reshape(n, heads, headDim), cos, sin, rotaryDim);
            Tensor kk = rope(k.forward(h).reshape(n, heads, headDim), cos, sin, rotaryDim);
            Tensor vv = v.forward(h).reshape(n, heads, headDim);
            Tensor attn = AttentionBackends.kernel().forward(qq, kk, vv, null,
                    AttentionContext.ragged(scale, false, heads, heads, headDim, cu));
            Tensor x1 = x.add(out.forward(attn.reshape(n, (long) heads * headDim)));
            Tensor mlp = fc2.forward(gelu.forward(fc1.forward(norm2.forward(x1))));
            return x1.add(mlp);
        }

        /** Rotate-half RoPE on the first {@code rotaryDim} dims of each head, fp32 math, pass-through for the rest. */
        private static Tensor rope(Tensor x, Tensor cos, Tensor sin, int rotaryDim) {
            long[] shape = x.shape();
            int half = rotaryDim / 2;
            int d = (int) shape[2];
            Tensor xf = x.to(ScalarType.Float);
            Tensor x1 = xf.get(Index.Colon, Index.Colon, Index.slice(0, half));
            Tensor x2 = xf.get(Index.Colon, Index.Colon, Index.slice(half, rotaryDim));
            Tensor o1 = x1.mul(cos).sub(x2.mul(sin));
            Tensor o2 = x2.mul(cos).add(x1.mul(sin));
            long rows = shape[0] * shape[1];
            Tensor out = o1.reshape(rows, half).to(x.dtype());
            Tensor second = o2.reshape(rows, half).to(x.dtype());
            if (rotaryDim == d) {
                return Tensor.hstack(out, second).reshape(shape);
            }
            Tensor pass = x.get(Index.Colon, Index.Colon, Index.slice(rotaryDim, d)).reshape(rows, d - rotaryDim);
            return Tensor.hstack(out, second, pass).reshape(shape);
        }
    }

    /** Checkpoint tensor name prefixes that belong to the vision side. */
    static boolean isVisionKey(String name) {
        return name.startsWith("vision_tower.") || name.startsWith("multi_modal_projector.")
                || name.startsWith("patch_merge_mlp.");
    }

    /**
     * Loads the vision weights of a checkpoint directory into a new tower on {@code device}.
     *
     * @param dir    directory with {@code config.json} and either {@code model.safetensors.index.json} or
     *               {@code *.safetensors} files holding the vision tensors.
     * @param device target device.
     * @param dtype  compute dtype (the checkpoint stores float32 patch embeddings and bfloat16 for the rest).
     * @return the loaded tower, or {@code null} when the checkpoint has no {@code vision_config}.
     * @throws IOException if tensors are missing or unreadable.
     */
    public static MiniMaxVisionTower load(File dir, Device device, ScalarType dtype) throws IOException {
        MiniMaxVisionArgs args = MiniMaxVisionArgs.fromConfigFile(new File(dir, "config.json"));
        if (args == null) {
            return null;
        }
        Map<String, List<String>> shardKeys = new HashMap<>();
        File index = new File(dir, "model.safetensors.index.json");
        if (index.exists()) {
            var map = new tools.jackson.databind.ObjectMapper().readTree(index).path("weight_map");
            for (var it = map.properties().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (isVisionKey(entry.getKey())) {
                    shardKeys.computeIfAbsent(entry.getValue().asString(), s -> new ArrayList<>()).add(entry.getKey());
                }
            }
        } else {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".safetensors"));
            if (files != null) {
                for (File f : files) {
                    for (String key : SafeTensors.listTensors(f.getPath())) {
                        if (isVisionKey(key)) {
                            shardKeys.computeIfAbsent(f.getName(), s -> new ArrayList<>()).add(key);
                        }
                    }
                }
            }
        }
        if (shardKeys.isEmpty()) {
            return null;
        }
        MiniMaxVisionTower tower = new MiniMaxVisionTower(args);
        tower.to(device, dtype);
        Map<String, Tensor> state = new HashMap<>();
        List<Tensor> owned = new ArrayList<>();
        try (var noGrad = Tensor.noGradGuard()) {
            for (var entry : shardKeys.entrySet()) {
                SafeTensors st = SafeTensors.read(Path.of(dir.getPath(), entry.getKey()).toString(), Device.CPU(),
                        entry.getValue());
                owned.addAll(st.tensors().values());
                for (String key : entry.getValue()) {
                    Tensor src = st.tensors().get(key);
                    String name = key.startsWith("vision_tower.") ? key.substring("vision_tower.".length()) : key;
                    if (name.endsWith("patch_embedding.weight")) {
                        // Conv3d [E, C, T, P, P] applied as the equivalent linear map [E, C*T*P*P].
                        src = src.reshape(src.shape()[0], -1);
                    }
                    Tensor onDevice = src.to(device, dtype);
                    owned.add(onDevice);
                    state.put(name, onDevice.contiguous());
                }
            }
            tower.loadStateDict(state, false);
        } finally {
            owned.forEach(Tensor::close);
        }
        tower.eval();
        return tower;
    }
}
