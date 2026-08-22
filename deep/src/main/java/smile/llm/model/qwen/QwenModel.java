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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Construct on CPU, load weights, then call {@link #to(Device)} to place
 * parameters and the RoPE frequency table. Install the KV pool afterward via
 * {@link #setKvCachePool} when full-attention layers are present.
 *
 * @author Haifeng Li
 */
public class QwenModel extends LayerBlock {
    private static final Logger logger = LoggerFactory.getLogger(QwenModel.class);

    final QwenModelArgs params;
    final int vocabSize;
    final int numLayers;
    final EmbeddingLayer tokEmbeddings;
    final List<QwenBlock> layers;
    final QwenRMSNorm norm;
    final LinearLayer lmHead;
    /** HF-style partial RoPE cos/sin tables (moved with {@link #to}). */
    PartialRotaryEncoding.CosSin rope;
    final TensorShardSpec shard;
    final TensorParallelGroup tpGroup;
    final int tpRank;
    KvCachePool kvCachePool;
    DeltaNetStatePool deltaNetStatePool;

    /**
     * Constructs the module graph on CPU. Call {@link #to(Device)} after weight
     * load; then {@link #setKvCachePool} when full-attention layers exist.
     *
     * @param args      hyperparameters.
     * @param statePool DeltaNet state pool (may be null when no linear layers).
     */
    public QwenModel(QwenModelArgs args, DeltaNetStatePool statePool) {
        this(args, statePool, null, null);
    }

    /**
     * Tensor-parallel shard constructor (CPU). Call {@link #to(Device)} after load.
     *
     * @param args      hyperparameters.
     * @param statePool DeltaNet state pool (may be null when no linear layers).
     * @param shard     local head / FFN shard description, or {@code null} for full width.
     * @param tpGroup   tensor-parallel group, or {@code null} for single-device.
     */
    public QwenModel(QwenModelArgs args, DeltaNetStatePool statePool,
                     TensorShardSpec shard, TensorParallelGroup tpGroup) {
        if (statePool == null && args.numLinearAttentionLayers() > 0) {
            throw new IllegalArgumentException("statePool required when linear-attention layers exist");
        }
        this.params = args;
        this.vocabSize = args.vocabSize();
        this.numLayers = args.numLayers();
        this.kvCachePool = null;
        this.deltaNetStatePool = statePool;
        this.shard = shard;
        this.tpGroup = tpGroup;
        this.tpRank = shard != null ? shard.tpRank() : 0;

        long t0 = System.currentTimeMillis();
        this.tokEmbeddings = new EmbeddingLayer(args.vocabSize(), args.dim());
        this.layers = new ArrayList<>();
        MemorySegment moduleList = smile_module_list_create();
        for (int i = 0; i < args.numLayers(); i++) {
            var block = new QwenBlock(i, args, statePool, shard, tpGroup);
            layers.add(block);
            smile_module_list_push_back(moduleList, block.module);
        }
        this.norm = new QwenRMSNorm(args.dim(), args.normEps());
        this.lmHead = new LinearLayer(args.dim(), args.vocabSize(), false);
        logger.info("tpRank={}: allocate layers ({}) in {} ms",
                tpRank, args.numLayers(), System.currentTimeMillis() - t0);

        long tRope = System.currentTimeMillis();
        this.rope = PartialRotaryEncoding.computeCosSin(
                args.rotaryDim(), args.maxSeqLen() * 2, args.ropeTheta());
        logger.info("tpRank={}: RoPE cos/sin (rotaryDim={}, end={}) in {} ms",
                tpRank, args.rotaryDim(), args.maxSeqLen() * 2, System.currentTimeMillis() - tRope);

        MemorySegment listAsModule = smile_module_list_as_module(moduleList);
        add("layers", listAsModule);
        smile_module_free(listAsModule);
        smile_module_list_free(moduleList);
        add("embed_tokens", tokEmbeddings);
        add("norm", norm);
        add("lm_head", lmHead);
    }

    /**
     * Moves parameters and the RoPE cos/sin tables to {@code device}.
     */
    @Override
    public QwenModel to(Device device) {
        super.to(device);
        moveRope(device);
        return this;
    }

    /**
     * Moves parameters and the RoPE cos/sin tables to {@code device} / {@code dtype}.
     * RoPE tables stay float32 (device move only).
     */
    @Override
    public QwenModel to(Device device, ScalarType dtype) {
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
     * Returns model hyperparameters.
     * @return model args.
     */
    public QwenModelArgs params() {
        return params;
    }

    /**
     * Returns the KV cache pool for full-attention layers, if installed.
     * @return KV pool, or {@code null} if unset.
     */
    public KvCachePool kvCachePool() {
        return kvCachePool;
    }

    /**
     * Returns the DeltaNet recurrent/conv state pool.
     * @return DeltaNet state pool, or {@code null} when unused.
     */
    public DeltaNetStatePool deltaNetStatePool() {
        return deltaNetStatePool;
    }

    /**
     * Returns the tensor-parallel shard description for this rank.
     * @return shard spec, or {@code null} for unsharded models.
     */
    public TensorShardSpec shard() {
        return shard;
    }

    /**
     * Returns this rank's tensor-parallel index.
     * @return TP rank ({@code 0} when unsharded).
     */
    public int tpRank() {
        return tpRank;
    }

    /**
     * Replaces the KV cache pool on every full-attention layer.
     *
     * @param pool           new KV pool (must not be {@code null}).
     * @param closePrevious  {@code true} to close the previous pool when replaced.
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
     * @return logits {@code [B, S, V]} in float32 (or {@code [B, 1, V]} when
     *         only the last position is scored — see {@link #forward(Tensor, int, boolean)}).
     */
    public Tensor forward(Tensor tokens, int startPos) {
        return forward(tokens, startPos, false);
    }

    /**
     * Forward pass.
     * @param tokens          token ids {@code [B, S]}.
     * @param startPos        cache start position.
     * @param allTokenLogits  when {@code false} and {@code S > 1}, run {@code lm_head}
     *                        only on the last hidden state (sampling / decode).
     *                        When {@code true}, score every position (logprobs).
     * @return logits in float32.
     */
    public Tensor forward(Tensor tokens, int startPos, boolean allTokenLogits) {
        long[] shape = tokens.shape();
        int seqlen = (int) shape[1];
        // Push a forward-local scope so intermediates are not retained by the
        // caller's Tensor.push(loopScope) until the whole generate step ends.
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        Device device = tokens.device();
        long freeBefore = cudaFreeBytes(device);
        if (freeBefore >= 0 && logger.isDebugEnabled()) {
            logger.debug("tpRank={}: forward start seqlen={} freeMiB={}",
                    tpRank, seqlen, freeBefore / (1024 * 1024));
        }
        try (var pos = Index.slice(startPos, startPos + seqlen)) {
            Tensor h = tokEmbeddings.forward(tokens);
            Tensor cos = rope.cos().get(pos);
            Tensor sin = rope.sin().get(pos);

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

            for (int i = 0; i < layers.size(); i++) {
                Tensor next = layers.get(i).forward(h, startPos, cos, sin, mask);
                h.close();
                h = next;
                if (logger.isDebugEnabled() && device.isCUDA() && (i + 1) % 8 == 0) {
                    long free = cudaFreeBytes(device);
                    if (free >= 0) {
                        logger.debug("tpRank={}: after layer {}/{} freeMiB={}",
                                tpRank, i + 1, layers.size(), free / (1024 * 1024));
                    }
                }
            }

            Tensor normalized = norm.forward(h);
            h.close();
            // mask is independently allocated; free before the vocab-sized lm_head.
            if (mask != null) {
                mask.close();
                mask = null;
            }
            // cos/sin are slices of long-lived tables — leave to AutoScope pop.
            Tensor logitsF;
            if (!allTokenLogits && seqlen > 1) {
                try (var last = Index.of(-1);
                     Tensor lastH = normalized.get(Index.Colon, last);
                     Tensor lastRow = lastH.unsqueeze(1)) {
                    logitsF = lmHead.forward(lastRow);
                }
                normalized.close();
            } else {
                logitsF = lmHead.forward(normalized);
                normalized.close();
            }
            Tensor logits = logitsF.to(ScalarType.Float);
            if (logits != logitsF) {
                logitsF.close();
            }
            logits.promoteToParent();
            return logits;
        } finally {
            Tensor.pop();
            long freeAfter = cudaFreeBytes(device);
            if (freeBefore >= 0 && freeAfter >= 0) {
                // Driver free delta after pop: usually caching-allocator HWM, not
                // live tensors (those should be closed). emptyCache at end of
                // generate returns unused blocks to the driver.
                long retainedMiB = (freeBefore - freeAfter) / (1024 * 1024);
                if (retainedMiB > 256 || logger.isDebugEnabled()) {
                    logger.info("tpRank={}: forward seqlen={} freeMiB {} -> {} after pop "
                                    + "(allocatorHwmDelta={} MiB; not necessarily a leak)",
                            tpRank, seqlen,
                            freeBefore / (1024 * 1024),
                            freeAfter / (1024 * 1024),
                            retainedMiB);
                }
            }
        }
    }

    /** Best-effort CUDA free bytes for diagnostics; {@code -1} when unavailable. */
    private static long cudaFreeBytes(Device device) {
        if (device == null || !device.isCUDA()) {
            return -1;
        }
        try {
            return smile.torch.Native.cudaMemGetInfo(device.index())[0];
        } catch (RuntimeException e) {
            return -1;
        }
    }

    @Override
    public Tensor forward(Tensor tokens) {
        return forward(tokens, 0);
    }
}
