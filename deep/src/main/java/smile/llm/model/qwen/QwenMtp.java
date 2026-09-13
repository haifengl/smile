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

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import smile.deep.layer.EmbeddingLayer;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCacheLayout;
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
 * Qwen3.5/3.8 native multi-token prediction (MTP) draft head.
 *
 * <p>Fuses the previous backbone hidden state with the embedding of the last
 * sampled token ({@code pre_fc_norm_*} + {@code fc}), runs one or more full-attention
 * transformer blocks, applies {@code norm}, then scores with the shared
 * {@code lm_head}. Multi-step drafts chain this module autoregressively
 * (NEXTN-style).
 *
 * @author Haifeng Li
 */
public class QwenMtp extends LayerBlock {
    final QwenModelArgs params;
    final int numLayers;
    final QwenRMSNorm preFcNormHidden;
    final QwenRMSNorm preFcNormEmbedding;
    final LinearLayer fc;
    final List<QwenBlock> layers;
    final QwenRMSNorm norm;
    /** Shared with the backbone (not owned). */
    EmbeddingLayer tokEmbeddings;
    /** Shared with the backbone (not owned). */
    LinearLayer lmHead;
    /** Shared RoPE tables from the backbone (not owned). */
    PartialRotaryEncoding.CosSin rope;
    KvCachePool kvCachePool;
    final TensorShardSpec shard;
    final TensorParallelGroup tpGroup;

    /**
     * Constructs MTP modules on CPU. Call {@link #to(Device)} after weight load;
     * then {@link #bindShared} and {@link #setKvCachePool}.
     *
     * @param args    backbone args ({@link QwenModelArgs#mtpNumHiddenLayers()} {@code > 0}).
     * @param shard   TP shard, or {@code null}.
     * @param tpGroup TP group, or {@code null}.
     */
    public QwenMtp(QwenModelArgs args, TensorShardSpec shard, TensorParallelGroup tpGroup) {
        if (args.mtpNumHiddenLayers() < 1) {
            throw new IllegalArgumentException("mtpNumHiddenLayers must be >= 1");
        }
        this.params = args;
        this.numLayers = args.mtpNumHiddenLayers();
        this.shard = shard;
        this.tpGroup = tpGroup;

        String[] layerTypes = new String[numLayers];
        java.util.Arrays.fill(layerTypes, QwenModelArgs.FULL_ATTENTION);
        // Synthetic args: full-attn only, short KV window for draft depth.
        int mtpSeq = QwenModelArgs.MAX_SPECULATIVE_TOKENS + 2;
        QwenModelArgs mtpArgs = new QwenModelArgs(
                args.dim(), numLayers, args.numHeads(), args.numKvHeads(), args.headDim(),
                args.vocabSize(), args.intermediateSize(), args.normEps(), args.ropeTheta(),
                args.partialRotaryFactor(), args.linearConvKernelDim(), args.linearKeyHeadDim(),
                args.linearValueHeadDim(), args.linearNumKeyHeads(), args.linearNumValueHeads(),
                layerTypes, args.maxBatchSize(), mtpSeq, 0, 0);

        this.preFcNormHidden = new QwenRMSNorm(args.dim(), args.normEps());
        this.preFcNormEmbedding = new QwenRMSNorm(args.dim(), args.normEps());
        this.fc = new LinearLayer(args.dim() * 2, args.dim(), false);
        this.norm = new QwenRMSNorm(args.dim(), args.normEps());
        this.layers = new ArrayList<>();
        MemorySegment moduleList = smile_module_list_create();
        for (int i = 0; i < numLayers; i++) {
            var block = new QwenBlock(i, mtpArgs, null, shard, tpGroup);
            layers.add(block);
            smile_module_list_push_back(moduleList, block.module);
        }
        MemorySegment listAsModule = smile_module_list_as_module(moduleList);
        add("layers", listAsModule);
        smile_module_free(listAsModule);
        smile_module_list_free(moduleList);
        add("pre_fc_norm_hidden", preFcNormHidden);
        add("pre_fc_norm_embedding", preFcNormEmbedding);
        add("fc", fc);
        add("norm", norm);
    }

    /**
     * Binds shared backbone embeddings, LM head, and RoPE tables.
     *
     * @param embeddings token embeddings.
     * @param lmHead     vocabulary projection.
     * @param rope       partial RoPE cos/sin tables.
     */
    public void bindShared(EmbeddingLayer embeddings, LinearLayer lmHead,
                           PartialRotaryEncoding.CosSin rope) {
        this.tokEmbeddings = embeddings;
        this.lmHead = lmHead;
        this.rope = rope;
    }

    /**
     * Installs an MTP-local KV pool and wires full-attention layers.
     *
     * @param pool           MTP KV pool.
     * @param closePrevious  whether to close a previous pool.
     */
    public void setKvCachePool(KvCachePool pool, boolean closePrevious) {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null");
        }
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

    /** @return MTP KV layout for a short draft window. */
    public KvCacheLayout kvCacheLayout() {
        int kvHeads = shard != null && shard.tpSize() > 1 ? shard.numKvHeads() : params.numKvHeads();
        return new KvCacheLayout(numLayers, kvHeads, params.headDim(),
                params.maxBatchSize(), QwenModelArgs.MAX_SPECULATIVE_TOKENS + 2);
    }

    /** @return MTP KV pool, or {@code null} before install. */
    public KvCachePool kvCachePool() {
        return kvCachePool;
    }

    /** @return number of MTP transformer layers. */
    public int numLayers() {
        return numLayers;
    }

    /**
     * One MTP draft step: fuse embedding(token) with previous hidden, run layers,
     * return float logits {@code [B, V]} and update {@code hiddenOut} with the
     * pre-lm-head hidden for the next draft step.
     *
     * @param tokenIds     token ids {@code [B]} (last sampled / previous draft).
     * @param prevHidden   previous hidden {@code [B, D]} (backbone or prior MTP).
     * @param position     absolute RoPE / cache write position for this step.
     * @param draftStep    zero-based index within the current draft window (MTP KV).
     * @return logits {@code [B, V]} in float32 (caller owns).
     */
    public Tensor draftStep(Tensor tokenIds, Tensor prevHidden, int position, int draftStep) {
        if (tokEmbeddings == null || lmHead == null || rope == null) {
            throw new IllegalStateException("MTP shared bindings not installed; call bindShared");
        }
        if (kvCachePool == null) {
            throw new IllegalStateException("MTP KV pool not installed; call setKvCachePool");
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor tokens = tokenIds.dim() == 1 ? tokenIds.unsqueeze(1) : tokenIds;
            Tensor embed = tokEmbeddings.forward(tokens);
            Tensor prev = prevHidden.dim() == 2 ? prevHidden.unsqueeze(1) : prevHidden;
            Tensor hNorm = preFcNormHidden.forward(prev);
            if (prev != prevHidden) {
                prev.close();
            }
            Tensor eNorm = preFcNormEmbedding.forward(embed);
            Tensor fused = PartialRotaryEncoding.concatLast(eNorm, hNorm);
            Tensor h = fc.forward(fused);
            embed.close();
            hNorm.close();
            eNorm.close();
            fused.close();

            Tensor cos = PartialRotaryEncoding.gather(rope.cos(), new int[]{position});
            Tensor sin = PartialRotaryEncoding.gather(rope.sin(), new int[]{position});
            int[] positions = new int[(int) h.shape()[0]];
            java.util.Arrays.fill(positions, draftStep);
            for (QwenBlock layer : layers) {
                Tensor next = layer.forward(h, positions, cos, sin, null);
                h.close();
                h = next;
            }
            cos.close();
            sin.close();

            Tensor normalized = norm.forward(h);
            h.close();
            Tensor logitsF = lmHead.forward(normalized);
            Tensor logits = logitsF.to(ScalarType.Float);
            if (logits != logitsF) {
                logitsF.close();
            }
            if (lastDraftHidden != null) {
                lastDraftHidden.close();
            }
            // NEXTN chains the post-norm MTP hidden (pre-lm_head).
            lastDraftHidden = normalized.detach();
            lastDraftHidden.detachFromScopes();
            logits.promoteToParent();
            return logits;
        } finally {
            Tensor.pop();
        }
    }

    /** Last MTP hidden (pre-lm-head) from {@link #draftStep}; owned by this module. */
    Tensor lastDraftHidden;

    /**
     * @return last draft hidden {@code [B, 1, D]} or {@code [B, D]} from the most
     *         recent {@link #draftStep}, or {@code null}.
     */
    public Tensor lastDraftHidden() {
        return lastDraftHidden;
    }

    /**
     * Clears MTP KV for a new speculative round (bind capacity for the draft window).
     *
     * @param draftWindow number of draft steps to reserve.
     */
    public void beginRound(int draftWindow) {
        if (kvCachePool == null) {
            return;
        }
        int cap = Math.max(1, draftWindow + 1);
        kvCachePool.unbindRequests();
        kvCachePool.bindRequests(1, cap);
    }

    @Override
    public QwenMtp to(Device device) {
        super.to(device);
        return this;
    }

    @Override
    public QwenMtp to(Device device, ScalarType dtype) {
        super.to(device, dtype);
        return this;
    }

    @Override
    public Tensor forward(Tensor input) {
        throw new UnsupportedOperationException("Use draftStep for MTP forwards");
    }
}
