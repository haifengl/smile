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
package smile.llm.qwen;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import smile.deep.layer.EmbeddingLayer;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCachePool;
import smile.llm.parallel.TensorParallelGroup;
import smile.llm.parallel.TensorShardSpec;
import smile.util.AutoScope;

import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_list_as_module;
import static smile.torch.smile_torch_h.smile_module_list_create;
import static smile.torch.smile_torch_h.smile_module_list_free;
import static smile.torch.smile_torch_h.smile_module_list_push_back;

/**
 * Qwen3.5 hybrid text model: embeddings, hybrid blocks, final norm, LM head.
 *
 * <p>When constructed with a {@link TensorShardSpec}, attention / FFN / DeltaNet
 * projections are locally sized for that TP rank. Embeddings and the LM head
 * remain replicated (full vocab) on each rank in phase 1.
 *
 * @author Haifeng Li
 */
public class QwenModel extends LayerBlock {
    final QwenModelArgs params;
    final int vocabSize;
    final int numLayers;
    final EmbeddingLayer tokEmbeddings;
    final List<QwenBlock> layers;
    final QwenRMSNorm norm;
    final LinearLayer lmHead;
    final Tensor cis;
    final TensorShardSpec shard;
    final TensorParallelGroup tpGroup;
    final int tpRank;
    KvCachePool kvCachePool;
    DeltaNetStatePool deltaNetStatePool;

    /**
     * Constructor.
     *
     * @param args        hyperparameters.
     * @param kvCachePool KV pool for full-attention layers.
     * @param statePool   DeltaNet state pool (may be null when no linear layers).
     * @param device      compute device.
     */
    public QwenModel(QwenModelArgs args, KvCachePool kvCachePool, DeltaNetStatePool statePool, Device device) {
        this(args, kvCachePool, statePool, device, null, null);
    }

    /**
     * Tensor-parallel shard constructor.
     */
    public QwenModel(QwenModelArgs args, KvCachePool kvCachePool, DeltaNetStatePool statePool,
                     Device device, TensorShardSpec shard, TensorParallelGroup tpGroup) {
        if (kvCachePool == null && args.numFullAttentionLayers() > 0) {
            throw new IllegalArgumentException("kvCachePool required when full-attention layers exist");
        }
        if (statePool == null && args.numLinearAttentionLayers() > 0) {
            throw new IllegalArgumentException("statePool required when linear-attention layers exist");
        }
        this.params = args;
        this.vocabSize = args.vocabSize();
        this.numLayers = args.numLayers();
        this.kvCachePool = kvCachePool;
        this.deltaNetStatePool = statePool;
        this.shard = shard;
        this.tpGroup = tpGroup;
        this.tpRank = shard != null ? shard.tpRank() : 0;

        this.tokEmbeddings = new EmbeddingLayer(args.vocabSize(), args.dim());
        this.layers = new ArrayList<>();
        MemorySegment moduleList = smile_module_list_create();
        for (int i = 0; i < args.numLayers(); i++) {
            var block = new QwenBlock(i, args, kvCachePool, statePool, shard, tpGroup);
            layers.add(block);
            smile_module_list_push_back(moduleList, block.module);
        }
        this.norm = new QwenRMSNorm(args.dim(), args.normEps());
        this.lmHead = new LinearLayer(args.dim(), args.vocabSize(), false);

        try (Tensor hostCis = PartialRotaryEncoding.computeFreqCis(
                args.rotaryDim(), args.maxSeqLen() * 2, args.ropeTheta())) {
            this.cis = hostCis.to(device);
        }

        MemorySegment listAsModule = smile_module_list_as_module(moduleList);
        add("layers", listAsModule);
        smile_module_free(listAsModule);
        smile_module_list_free(moduleList);
        add("embed_tokens", tokEmbeddings);
        add("norm", norm);
        add("lm_head", lmHead);
        to(device);
    }

    /**
     * Convenience constructor that allocates test-sized pools on the given device.
     */
    public QwenModel(QwenModelArgs args, Device device) {
        this(args,
                args.numFullAttentionLayers() > 0
                        ? KvCachePool.forTesting(args.kvCacheLayout(), device) : null,
                args.numLinearAttentionLayers() > 0
                        ? new DeltaNetStatePool(
                        args.numLinearAttentionLayers(),
                        args.linearNumValueHeads(),
                        args.linearKeyHeadDim(),
                        args.linearValueHeadDim(),
                        args.linearConvDim(),
                        args.linearConvKernelDim(),
                        args.maxBatchSize(),
                        device,
                        ScalarType.Float)
                        : null,
                device);
    }

    public QwenModelArgs params() {
        return params;
    }

    public KvCachePool kvCachePool() {
        return kvCachePool;
    }

    public DeltaNetStatePool deltaNetStatePool() {
        return deltaNetStatePool;
    }

    public TensorShardSpec shard() {
        return shard;
    }

    public int tpRank() {
        return tpRank;
    }

    /**
     * Replaces the KV cache pool on every full-attention layer.
     */
    public void setKvCachePool(KvCachePool pool, boolean closePrevious) {
        if (pool == null) throw new IllegalArgumentException("pool must not be null");
        var previous = this.kvCachePool;
        this.kvCachePool = pool;
        for (var layer : layers) {
            if (layer.selfAttn != null) {
                layer.selfAttn.setCachePool(pool);
            }
        }
        if (closePrevious && previous != null && previous != pool) {
            previous.close();
        }
    }

    /**
     * Forward pass.
     * @param tokens   token ids {@code [B, S]}.
     * @param startPos cache start position.
     * @return logits {@code [B, S, V]} in float32.
     */
    public Tensor forward(Tensor tokens, int startPos) {
        long[] shape = tokens.shape();
        int seqlen = (int) shape[1];
        // Push a forward-local scope so intermediates are not retained by the
        // caller's Tensor.push(loopScope) until the whole generate step ends.
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try (var pos = Index.slice(startPos, startPos + seqlen)) {
            Tensor h = tokEmbeddings.forward(tokens);
            Tensor freqs = cis.get(pos);

            Tensor mask = null;
            if (seqlen > 1) {
                // Allocate on h's device — global Tensor.setDefaultOptions is the
                // last TP rank under multi-GPU, so default full/zeros would race.
                var maskOpts = new Tensor.Options()
                        .device(h.device())
                        .dtype(ScalarType.Float)
                        .requireGradients(false);
                mask = Tensor.zeros(maskOpts, seqlen, seqlen).fill_(Float.NEGATIVE_INFINITY);
                mask.triu_(1);
                if (startPos > 0) {
                    try (var zeros = Tensor.zeros(maskOpts, seqlen, startPos)) {
                        Tensor prev = mask;
                        mask = Tensor.hstack(zeros, prev);
                        prev.close();
                    }
                }
                if (mask.dtype() != h.dtype()) {
                    Tensor maskF = mask;
                    mask = maskF.to(h.dtype());
                    maskF.close();
                }
            }

            for (var layer : layers) {
                Tensor next = layer.forward(h, startPos, freqs, mask);
                h.close();
                h = next;
            }

            Tensor normalized = norm.forward(h);
            h.close();
            Tensor logitsF = lmHead.forward(normalized);
            Tensor logits = logitsF.to(ScalarType.Float);
            // Keep logits alive after pop(); everything else is freed.
            scope.remove(logits);
            return logits;
        } finally {
            Tensor.pop();
        }
    }

    @Override
    public Tensor forward(Tensor tokens) {
        return forward(tokens, 0);
    }
}
