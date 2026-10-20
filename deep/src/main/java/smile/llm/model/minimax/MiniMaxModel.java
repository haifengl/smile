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

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import smile.deep.layer.EmbeddingLayer;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCachePool;
import smile.llm.model.qwen.PartialRotaryEncoding;
import smile.llm.model.qwen.QwenRMSNorm;
import smile.util.AutoScope;

import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_list_as_module;
import static smile.torch.smile_torch_h.smile_module_list_create;
import static smile.torch.smile_torch_h.smile_module_list_free;
import static smile.torch.smile_torch_h.smile_module_list_push_back;

/**
 * MiniMax-M3 text model: embeddings, decoder blocks, final norm and LM head.
 *
 * <p>Construct on CPU, load weights, call {@link #to(Device)} to place parameters and the RoPE
 * tables, then install the KV pool with {@link #setKvCachePool}. This first version is the
 * eager reference path (dense attention, host-routed MoE): no CUDA graphs, no tensor parallelism.
 *
 * @author Haifeng Li
 */
public class MiniMaxModel extends LayerBlock {
    final MiniMaxModelArgs params;
    final MiniMaxShard shard;
    final EmbeddingLayer tokEmbeddings;
    final List<MiniMaxBlock> layers = new ArrayList<>();
    final QwenRMSNorm norm;
    final LinearLayer lmHead;
    PartialRotaryEncoding.CosSin rope;
    KvCachePool kvCachePool;
    MiniMaxIndexCache indexCache;

    /**
     * Constructs the module graph on CPU.
     *
     * @param args hyperparameters.
     */
    public MiniMaxModel(MiniMaxModelArgs args) {
        this(args, MiniMaxShard.single(), null);
    }

    /**
     * Expert-parallel constructor: one model replica per rank, each owning a slice of the experts.
     *
     * @param args  hyperparameters.
     * @param shard this rank's expert slice (its expert weights are allocated on {@code shard.device()}).
     * @param group all-reduce group, or {@code null} for a single rank.
     */
    public MiniMaxModel(MiniMaxModelArgs args, MiniMaxShard shard, smile.llm.parallel.TensorParallelGroup group) {
        super();
        this.params = args;
        this.shard = shard;
        this.tokEmbeddings = new EmbeddingLayer(args.vocabSize(), args.dim());
        MemorySegment list = smile_module_list_create();
        for (int i = 0; i < args.numLayers(); i++) {
            MiniMaxBlock block = new MiniMaxBlock(i, args, shard, group);
            layers.add(block);
            smile_module_list_push_back(list, block.module);
        }
        this.norm = new QwenRMSNorm(args.dim(), args.normEps());
        this.lmHead = new LinearLayer(args.dim(), args.vocabSize(), false);
        this.rope = PartialRotaryEncoding.computeCosSin(
                args.rotaryDim(), args.maxSeqLen() * 2, args.ropeTheta());

        MemorySegment listModule = smile_module_list_as_module(list);
        add("layers", listModule);
        smile_module_free(listModule);
        smile_module_list_free(list);
        add("embed_tokens", tokEmbeddings);
        add("norm", norm);
        add("lm_head", lmHead);
    }

    /**
     * Returns the hyperparameters.
     * @return model args.
     */
    public MiniMaxModelArgs params() {
        return params;
    }

    /**
     * Returns this rank's expert slice.
     * @return the shard.
     */
    public MiniMaxShard shard() {
        return shard;
    }

    /**
     * Returns the FP8-resident (Marlin) linear layers by their SMILE weight names, e.g.
     * {@code layers.3.self_attn.q_proj.weight}; empty unless the model was built in an MXFP8 weight mode.
     * Routed experts are loaded through {@link MiniMaxMoE} instead.
     *
     * @return name to layer map.
     */
    public java.util.Map<String, smile.llm.quant.MxFp8Layer> quantLinears() {
        var out = new java.util.LinkedHashMap<String, smile.llm.quant.MxFp8Layer>();
        for (MiniMaxBlock layer : layers) {
            layer.collect(out);
        }
        return out;
    }

    /**
     * Returns the decoder blocks.
     * @return blocks in layer order.
     */
    public List<MiniMaxBlock> layers() {
        return layers;
    }

    /**
     * Returns the installed KV pool.
     * @return KV pool, or {@code null} before {@link #setKvCachePool}.
     */
    public KvCachePool kvCachePool() {
        return kvCachePool;
    }

    @Override
    public MiniMaxModel to(Device device) {
        super.to(device);
        moveRope(device);
        return this;
    }

    @Override
    public MiniMaxModel to(Device device, ScalarType dtype) {
        super.to(device, dtype);
        moveRope(device);
        return this;
    }

    private void moveRope(Device device) {
        Tensor cos = rope.cos().to(device);
        Tensor sin = rope.sin().to(device);
        if (cos != rope.cos() || sin != rope.sin()) {
            cos.detachFromScopes();
            sin.detachFromScopes();
            rope.close();
            rope = new PartialRotaryEncoding.CosSin(cos, sin);
        }
    }

    /**
     * Installs the KV pool in every attention layer.
     *
     * @param pool          new KV pool (must not be {@code null}).
     * @param closePrevious {@code true} to close the previous pool when replaced.
     */
    public void setKvCachePool(KvCachePool pool, boolean closePrevious) {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null");
        }
        var previous = this.kvCachePool;
        this.kvCachePool = pool;
        if (indexCache != null) {
            indexCache.close();
            indexCache = null;
        }
        int sparseLayers = params.numSparseLayers();
        if (sparseLayers > 0) {
            indexCache = new MiniMaxIndexCache(sparseLayers, pool.numSlots(), params.numIndexKeyHeads(),
                    params.indexDim(), pool.device(), pool.computeDtype());
        }
        int ordinal = 0;
        for (var layer : layers) {
            layer.selfAttn.setCachePool(pool);
            if (params.isSparse(layer.layerId())) {
                layer.selfAttn.setIndexCache(indexCache, ordinal++);
            }
        }
        if (closePrevious && previous != null && previous != pool) {
            previous.close();
        }
    }

    /**
     * Prefill / multi-token window forward at a uniform cache position.
     *
     * @param tokens         token ids {@code [B, S]}.
     * @param startPos       cache start position.
     * @param allTokenLogits score every position when {@code true}, otherwise only the last.
     * @return float32 logits.
     */
    public Tensor forward(Tensor tokens, int startPos, boolean allTokenLogits) {
        int batch = (int) tokens.shape()[0];
        int seqlen = (int) tokens.shape()[1];
        int[] positions = new int[batch];
        Arrays.fill(positions, startPos);
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        MiniMaxStep step = MiniMaxStep.begin(positions);
        try (var window = Index.slice(startPos, startPos + seqlen)) {
            Tensor cos = rope.cos().get(window);
            Tensor sin = rope.sin().get(window);
            Tensor h = tokEmbeddings.forward(tokens);
            Tensor mask = seqlen > 1 ? causalMask(seqlen, startPos, h) : null;
            Tensor logits = run(h, positions, cos, sin, mask, allTokenLogits);
            logits.promoteToParent();
            return logits;
        } finally {
            step.close();
            Tensor.pop();
        }
    }

    /**
     * Single-token decode with per-row absolute positions.
     *
     * @param tokens    token ids {@code [B, 1]}.
     * @param positions write position per batch row (also the RoPE position).
     * @return float32 logits {@code [B, 1, V]}.
     */
    public Tensor forward(Tensor tokens, int[] positions) {
        if (positions == null || positions.length != (int) tokens.shape()[0]) {
            throw new IllegalArgumentException("positions length must equal batch size");
        }
        if (tokens.shape()[1] != 1) {
            throw new IllegalArgumentException("ragged forward requires seqLen == 1");
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        MiniMaxStep step = MiniMaxStep.begin(positions);
        try {
            Tensor cos = PartialRotaryEncoding.gather(rope.cos(), positions);
            Tensor sin = PartialRotaryEncoding.gather(rope.sin(), positions);
            Tensor h = tokEmbeddings.forward(tokens);
            Tensor logits = run(h, positions, cos, sin, null, false);
            logits.promoteToParent();
            return logits;
        } finally {
            step.close();
            Tensor.pop();
        }
    }

    @Override
    public Tensor forward(Tensor tokens) {
        return forward(tokens, 0, false);
    }

    private Tensor run(Tensor h, int[] positions, Tensor cos, Tensor sin, Tensor mask,
                       boolean allTokenLogits) {
        for (MiniMaxBlock layer : layers) {
            h = layer.forward(h, positions, cos, sin, mask);
        }
        Tensor normalized = norm.forward(h);
        int seqlen = (int) normalized.shape()[1];
        Tensor logits;
        if (!allTokenLogits && seqlen > 1) {
            try (var last = Index.of(-1)) {
                Tensor lastRow = normalized.get(Index.Colon, last).unsqueeze(1);
                logits = lmHead.forward(lastRow);
            }
        } else {
            logits = lmHead.forward(normalized);
        }
        return logits.to(ScalarType.Float);
    }

    private static Tensor causalMask(int seqlen, int startPos, Tensor like) {
        var opts = new Tensor.Options().device(like.device()).dtype(ScalarType.Float).requireGradients(false);
        Tensor mask = Tensor.zeros(opts, seqlen, seqlen).fill_(Float.NEGATIVE_INFINITY);
        mask.triu_(1);
        if (startPos > 0) {
            Tensor zeros = Tensor.zeros(opts, seqlen, startPos);
            mask = Tensor.hstack(zeros, mask);
        }
        return mask.to(like.dtype());
    }
}
