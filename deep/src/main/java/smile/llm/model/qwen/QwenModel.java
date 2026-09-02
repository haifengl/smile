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
import smile.llm.engine.DecodeCudaGraph;
import smile.llm.engine.DecodeCudaGraphSession;
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
    /** Optional native vision tower (Qwen3.8); null for text-only. */
    final QwenVisionTower visual;
    final QwenVisionArgs visionArgs;
    final TensorShardSpec shard;
    final TensorParallelGroup tpGroup;
    final int tpRank;
    KvCachePool kvCachePool;
    DeltaNetStatePool deltaNetStatePool;
    /** Per-rank CUDA graph session for batch-1 decode (Phase 2c). */
    DecodeCudaGraphSession decodeGraphSession;
    /** Stable RoPE gather buffers for graph capture / replay. */
    Tensor decodeGraphCosBuf;
    Tensor decodeGraphSinBuf;
    /** Logits tensor captured inside the decode graph (do not close). */
    Tensor decodeGraphLogitsOut;

    /**
     * Constructs the module graph on CPU. Call {@link #to(Device)} after weight
     * load; then {@link #setKvCachePool} when full-attention layers exist.
     *
     * @param args      hyperparameters.
     * @param statePool DeltaNet state pool (may be null when no linear layers).
     */
    public QwenModel(QwenModelArgs args, DeltaNetStatePool statePool) {
        this(args, statePool, null, null, null);
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
        this(args, statePool, shard, tpGroup, null);
    }

    /**
     * Multimodal constructor with optional vision tower.
     *
     * @param args       text hyperparameters.
     * @param statePool  DeltaNet state pool.
     * @param shard      TP shard, or null.
     * @param tpGroup    TP group, or null.
     * @param visionArgs vision hyperparameters, or {@code null} for text-only.
     */
    public QwenModel(QwenModelArgs args, DeltaNetStatePool statePool,
                     TensorShardSpec shard, TensorParallelGroup tpGroup,
                     QwenVisionArgs visionArgs) {
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
        this.visionArgs = visionArgs;

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

        if (visionArgs != null) {
            long tVis = System.currentTimeMillis();
            this.visual = new QwenVisionTower(visionArgs);
            add("visual", visual);
            logger.info("tpRank={}: vision tower (depth={}) in {} ms",
                    tpRank, visionArgs.depth(), System.currentTimeMillis() - tVis);
        } else {
            this.visual = null;
        }

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

    /** @return number of hybrid decoder blocks. */
    public int numLayers() {
        return numLayers;
    }

    /** @return decoder blocks (owned by this model). */
    public List<QwenBlock> layers() {
        return layers;
    }

    /**
     * @return vision tower, or {@code null} when text-only.
     */
    public QwenVisionTower visual() {
        return visual;
    }

    /**
     * @return vision args, or {@code null} when text-only.
     */
    public QwenVisionArgs visionArgs() {
        return visionArgs;
    }

    /**
     * Token embedding lookup (for multimodal splice).
     *
     * @param tokens token ids.
     * @return embeddings.
     */
    public Tensor embedTokens(Tensor tokens) {
        return tokEmbeddings.forward(tokens);
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

    /**
     * Prefill from precomputed embeddings (multimodal splice) with optional
     * interleaved mRoPE cos/sin. When {@code cos}/{@code sin} are null, uses
     * the standard 1D RoPE table slice for {@code [startPos, startPos+S)}.
     *
     * @param inputsEmbeds   {@code [B, S, D]} hidden states.
     * @param startPos       cache start position.
     * @param cos            optional {@code [S, rotaryDim]} (or null).
     * @param sin            optional {@code [S, rotaryDim]} (or null).
     * @param allTokenLogits whether to score every position.
     * @return logits in float32.
     */
    public Tensor forwardEmbeds(Tensor inputsEmbeds, int startPos,
                                Tensor cos, Tensor sin, boolean allTokenLogits) {
        long[] shape = inputsEmbeds.shape();
        int seqlen = (int) shape[1];
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        Device device = inputsEmbeds.device();
        try {
            Tensor h = inputsEmbeds;
            Tensor cosUse = cos;
            Tensor sinUse = sin;
            boolean ownRoPE = false;
            if (cosUse == null || sinUse == null) {
                try (var pos = Index.slice(startPos, startPos + seqlen)) {
                    cosUse = rope.cos().get(pos);
                    sinUse = rope.sin().get(pos);
                    ownRoPE = false; // slices of long-lived tables
                }
            }

            Tensor mask = null;
            if (seqlen > 1) {
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

            // Clone embeds so we can close intermediates without freeing caller tensor.
            h = inputsEmbeds.copy();
            for (int i = 0; i < layers.size(); i++) {
                Tensor next = layers.get(i).forward(h, startPos, cosUse, sinUse, mask);
                h.close();
                h = next;
            }

            Tensor normalized = norm.forward(h);
            h.close();
            if (mask != null) {
                mask.close();
            }
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
        }
    }

    /**
     * Replaces image/video pad rows in text embeddings with vision features.
     *
     * @param embeds        {@code [1, S, D]} text embeddings (mutated copy returned).
     * @param inputIds      length {@code S} token ids.
     * @param visionEmbeds  {@code [N, D]} vision tokens in pad order.
     * @param imageTokenId  image pad id.
     * @param videoTokenId  video pad id.
     * @return spliced embeddings {@code [1, S, D]} (caller owns).
     */
    public static Tensor spliceVisionEmbeds(Tensor embeds, int[] inputIds, Tensor visionEmbeds,
                                            int imageTokenId, int videoTokenId) {
        if (embeds == null || inputIds == null || visionEmbeds == null) {
            throw new IllegalArgumentException("embeds, inputIds, visionEmbeds required");
        }
        List<Integer> padIdx = new ArrayList<>();
        for (int i = 0; i < inputIds.length; i++) {
            if (inputIds[i] == imageTokenId || inputIds[i] == videoTokenId) {
                padIdx.add(i);
            }
        }
        long nVis = visionEmbeds.shape()[0];
        if (padIdx.size() != nVis) {
            throw new IllegalArgumentException(
                    "pad count " + padIdx.size() + " != vision tokens " + nVis);
        }
        Tensor out = embeds.copy();
        for (int i = 0; i < padIdx.size(); i++) {
            int pos = padIdx.get(i);
            try (var row = Index.of(0);
                 var col = Index.of(pos);
                 var visRow = Index.of(i);
                 Tensor src = visionEmbeds.get(visRow)) {
                out.put_(src, row, col);
            }
        }
        out.promoteToParent();
        return out;
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

    /**
     * Decode forward with per-row absolute positions ({@code seqLen} must be 1).
     *
     * @param tokens    token ids {@code [B, 1]}.
     * @param positions write position per batch row.
     * @return logits {@code [B, 1, V]}.
     */
    public Tensor forward(Tensor tokens, int[] positions) {
        return forward(tokens, positions, false);
    }

    /**
     * Decode forward with per-row absolute positions.
     *
     * @param tokens         token ids {@code [B, 1]}.
     * @param positions      write position per batch row.
     * @param allTokenLogits unused for {@code S == 1} (kept for API symmetry).
     * @return logits in float32.
     */
    public Tensor forward(Tensor tokens, int[] positions, boolean allTokenLogits) {
        return forward(tokens, positions, positions, allTokenLogits);
    }

    /**
     * Decode forward with separate KV write positions and RoPE gather positions
     * (needed for multimodal {@code rope_delta}).
     *
     * @param tokens         token ids {@code [B, 1]}.
     * @param cachePositions KV write positions.
     * @param ropePositions  RoPE table gather positions.
     * @param allTokenLogits unused for {@code S == 1}.
     * @return logits in float32.
     */
    public Tensor forward(Tensor tokens, int[] cachePositions, int[] ropePositions,
                          boolean allTokenLogits) {
        if (cachePositions == null || cachePositions.length != (int) tokens.shape()[0]) {
            throw new IllegalArgumentException("cachePositions length must equal batch size");
        }
        if (ropePositions == null || ropePositions.length != cachePositions.length) {
            throw new IllegalArgumentException("ropePositions length must equal batch size");
        }
        if (tokens.shape()[1] != 1) {
            throw new IllegalArgumentException("ragged forward requires seqLen == 1");
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        Device device = tokens.device();
        long freeBefore = cudaFreeBytes(device);
        try {
            Tensor cos = PartialRotaryEncoding.gather(rope.cos(), ropePositions);
            Tensor sin = PartialRotaryEncoding.gather(rope.sin(), ropePositions);
            try {
                return forwardRaggedDecodeCore(tokens, cachePositions, cos, sin);
            } finally {
                cos.close();
                sin.close();
            }
        } finally {
            Tensor.pop();
            long freeAfter = cudaFreeBytes(device);
            if (freeBefore >= 0 && freeAfter >= 0 && logger.isDebugEnabled()) {
                logger.debug("tpRank={}: ragged forward freeMiB {} -> {}",
                        tpRank,
                        freeBefore / (1024 * 1024),
                        freeAfter / (1024 * 1024));
            }
        }
    }

    /**
     * Batch-1 decode forward with optional CUDA graph capture / replay.
     *
     * @param tokens         token ids {@code [1, 1]}.
     * @param cachePositions KV write positions ({@code length == 1}).
     * @param ropePositions  RoPE table gather positions.
     * @return logits in float32.
     */
    public Tensor forwardDecodeGraph(Tensor tokens, int[] cachePositions, int[] ropePositions) {
        if (!DecodeCudaGraph.enabled() || kvCachePool == null) {
            return forward(tokens, cachePositions, ropePositions, false);
        }
        if (decodeGraphSession == null) {
            decodeGraphSession = DecodeCudaGraphSession.tryCreate();
        }
        if (decodeGraphSession == null) {
            return forward(tokens, cachePositions, ropePositions, false);
        }

        int cacheLen = cachePositions[0] + 1;
        int numPages = kvCachePool.numPagesForLength(cacheLen);
        kvCachePool.setDecodeGraphBuffers(true);
        try {
            ensureDecodeGraphRoPEBuffers(tokens.device());
            prepareDecodeGraphInputs(cachePositions, ropePositions, cacheLen);

            if (decodeGraphSession.canReplay(numPages)) {
                decodeGraphSession.replay();
                DecodeCudaGraph.markPersistentLogits(true);
                return decodeGraphLogitsOut;
            }

            boolean capture = decodeGraphSession.shouldCapture(numPages);
            if (capture) {
                int deviceIndex = Byte.toUnsignedInt(tokens.device().index());
                try {
                    decodeGraphSession.beginCapture(deviceIndex);
                    try {
                        decodeGraphLogitsOut = forwardRaggedDecodeCore(
                                tokens, cachePositions, decodeGraphCosBuf, decodeGraphSinBuf);
                        decodeGraphLogitsOut.detachFromScopes();
                    } finally {
                        decodeGraphSession.endCapture();
                    }
                    if (decodeGraphSession.canReplay(numPages)) {
                        DecodeCudaGraph.markPersistentLogits(true);
                        return decodeGraphLogitsOut;
                    }
                    logger.warn("tpRank={}: CUDA graph capture did not produce a replayable graph",
                            tpRank);
                    DecodeCudaGraph.disableCapture("capture incomplete");
                    DecodeCudaGraph.markPersistentLogits(false);
                    decodeGraphSession.close();
                    decodeGraphSession = null;
                    decodeGraphLogitsOut = null;
                } catch (RuntimeException e) {
                    logger.warn("tpRank={}: CUDA graph capture failed, falling back to eager: {}",
                            tpRank, e.getMessage());
                    DecodeCudaGraph.disableCapture(e.getMessage());
                    DecodeCudaGraph.markPersistentLogits(false);
                    if (decodeGraphSession != null) {
                        decodeGraphSession.close();
                        decodeGraphSession = null;
                    }
                    decodeGraphLogitsOut = null;
                    kvCachePool.setDecodeGraphBuffers(false);
                    return forward(tokens, cachePositions, ropePositions, false);
                }
            }

            return forwardRaggedDecodeCore(tokens, cachePositions, decodeGraphCosBuf, decodeGraphSinBuf);
        } finally {
            kvCachePool.setDecodeGraphBuffers(false);
        }
    }

    /** Releases CUDA graph resources for this rank. */
    public void closeDecodeGraph() {
        if (decodeGraphSession != null) {
            decodeGraphSession.close();
            decodeGraphSession = null;
        }
        if (decodeGraphCosBuf != null) {
            decodeGraphCosBuf.close();
            decodeGraphCosBuf = null;
        }
        if (decodeGraphSinBuf != null) {
            decodeGraphSinBuf.close();
            decodeGraphSinBuf = null;
        }
        decodeGraphLogitsOut = null;
    }

    private void ensureDecodeGraphRoPEBuffers(Device device) {
        if (decodeGraphCosBuf != null) {
            return;
        }
        int rotaryDim = params.rotaryDim();
        var opts = new Tensor.Options().device(device).dtype(ScalarType.Float);
        decodeGraphCosBuf = Tensor.zeros(opts, 1, 1, rotaryDim);
        decodeGraphSinBuf = Tensor.zeros(opts, 1, 1, rotaryDim);
        decodeGraphCosBuf.detachFromScopes();
        decodeGraphSinBuf.detachFromScopes();
    }

    private void prepareDecodeGraphInputs(int[] cachePositions, int[] ropePositions, int cacheLen) {
        PartialRotaryEncoding.gatherInto(rope.cos(), ropePositions, decodeGraphCosBuf);
        PartialRotaryEncoding.gatherInto(rope.sin(), ropePositions, decodeGraphSinBuf);
        kvCachePool.prepareDecodeGraphStep(cacheLen, cachePositions[0]);
    }

    private Tensor forwardRaggedDecodeCore(Tensor tokens, int[] cachePositions,
                                           Tensor cos, Tensor sin) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor h = tokEmbeddings.forward(tokens);
            for (int i = 0; i < layers.size(); i++) {
                Tensor next = layers.get(i).forward(h, cachePositions, cos, sin, null);
                h.close();
                h = next;
            }
            Tensor normalized = norm.forward(h);
            h.close();
            Tensor logitsF = lmHead.forward(normalized);
            normalized.close();
            Tensor logits = logitsF.to(ScalarType.Float);
            if (logits != logitsF) {
                logitsF.close();
            }
            logits.promoteToParent();
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
