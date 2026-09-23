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
import java.util.Arrays;
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
import smile.llm.attention.AttentionBackend;
import smile.llm.attention.AttentionBackends;
import smile.llm.cache.FlashInferKvMetadata;
import smile.llm.cache.KvCachePool;
import smile.llm.engine.DecodeCudaGraph;
import smile.llm.engine.DecodeCudaGraphLog;
import smile.llm.engine.DecodeCudaGraphSession;
import smile.llm.engine.DecodeForwardProfile;
import smile.llm.engine.VerifyCudaGraph;
import smile.llm.engine.VerifyCudaGraphSession;
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
    /** Optional native MTP draft head; null when {@code mtp_num_hidden_layers == 0}. */
    final QwenMtp mtp;
    /**
     * Last backbone post-final-norm hidden (detached) for MTP anchoring; null when
     * unused. Named historically; matches vLLM/SGLang which pass {@code model.norm}
     * output into the MTP {@code pre_fc_norm_*} fusion.
     *
     * <p>Shared by every forward (decode/prefill/verify) for whichever batch just
     * ran — {@code Qwen} scatters it into {@link #mtpAnchorPool} by request id
     * immediately after each forward, since this field alone cannot survive a
     * later forward for a different batch.
     */
    Tensor lastPreNormHidden;
    /** Per-request durable MTP anchor store; null when {@link #mtp} is null. */
    final MtpAnchorPool mtpAnchorPool;
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
    /** Per-rank CUDA graph session for uniform decode (Phase 2c/2d). */
    DecodeCudaGraphSession decodeGraphSession;
    /** Prefetched graph for the next {@code numPages} bucket (Phase 2e). */
    DecodeCudaGraphSession decodeGraphPrefetchSession;
    int prefetchTargetBatch = -1;
    int prefetchTargetNumPages = -1;
    /** Last uniform decode step (for idle prefetch continuation). */
    int lastDecodeGraphBatch = -1;
    int lastDecodeGraphNumPages = -1;
    int lastDecodeGraphCacheLen = -1;
    int lastDecodeGraphCachePos = -1;
    int[] lastDecodeGraphRopePos;
    /** FlashInfer CSR kept alive for a prefetched graph (same tensors capture/replay must share). */
    FlashInferKvMetadata prefetchedStepMeta;
    int prefetchedStepMetaLen = -1;
    /** Stable token buffer for graph capture / replay. */
    Tensor decodeGraphTokenBuf;
    /** Stable RoPE gather buffers for graph capture / replay. */
    Tensor decodeGraphCosBuf;
    Tensor decodeGraphSinBuf;
    /** Logits tensor captured inside the decode graph (do not close). */
    Tensor decodeGraphLogitsOut;
    /** Pre-capture logits buffer (stable address outside the graph memory pool). */
    Tensor decodeGraphLogitsBuf;

    /** Per-rank CUDA graph session for the uniform MTP window-verify forward. */
    VerifyCudaGraphSession verifyGraphSession;
    /** Stable token buffer {@code [B, windowLen]} for verify graph capture / replay. */
    Tensor verifyGraphTokenBuf;
    /** Stable RoPE gather buffers {@code [windowLen, rotaryDim]} for verify graph capture / replay. */
    Tensor verifyGraphCosBuf;
    Tensor verifyGraphSinBuf;
    /** Logits tensor captured inside the verify graph (do not close). */
    Tensor verifyGraphLogitsOut;
    /** Pre-capture logits buffer (stable address outside the graph memory pool). */
    Tensor verifyGraphLogitsBuf;
    /**
     * Retained per-position post-final-norm hidden {@code [1, S, D]} for the
     * verify-window forward, populated only while
     * {@link DeltaNetStatePool#verifyWindowActive()}. On a partial MTP accept,
     * {@link #setMtpAnchorAtWindowPosition} restores the anchor from row
     * {@code r} here instead of a second full-window forward.
     */
    Tensor verifyWindowNormalizedBuf;

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

        if (args.hasMtp()) {
            long tMtp = System.currentTimeMillis();
            this.mtp = new QwenMtp(args, shard, tpGroup);
            this.mtp.bindShared(tokEmbeddings, lmHead, rope);
            add("mtp", mtp);
            this.mtpAnchorPool = new MtpAnchorPool(args.maxBatchSize(), args.dim());
            logger.info("tpRank={}: MTP head (layers={}) in {} ms",
                    tpRank, args.mtpNumHiddenLayers(), System.currentTimeMillis() - tMtp);
        } else {
            this.mtp = null;
            this.mtpAnchorPool = null;
        }

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
            if (mtp != null) {
                mtp.bindShared(tokEmbeddings, lmHead, rope);
            }
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
     * Returns the number of hybrid decoder blocks.
     *
     * @return number of hybrid decoder blocks.
     */
    public int numLayers() {
        return numLayers;
    }

    /**
     * Returns the decoder blocks.
     *
     * @return decoder blocks (owned by this model).
     */
    public List<QwenBlock> layers() {
        return layers;
    }

    /**
     * Returns the vision tower.
     *
     * @return vision tower, or {@code null} when text-only.
     */
    public QwenVisionTower visual() {
        return visual;
    }

    /**
     * Returns the vision args.
     *
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
     * Returns the per-request MTP anchor store.
     * @return anchor pool, or {@code null} when {@link #mtp()} is null.
     */
    public MtpAnchorPool mtpAnchorPool() {
        return mtpAnchorPool;
    }

    /**
     * Returns the native MTP draft head.
     *
     * @return native MTP draft head, or {@code null} when not configured.
     */
    public QwenMtp mtp() {
        return mtp;
    }

    /**
     * Returns the last captured post-final-norm hidden for MTP.
     *
     * @return last pre-norm hidden, or {@code null}.
     */
    public Tensor lastPreNormHidden() {
        return lastPreNormHidden;
    }

    /**
     * Stores the last-token post-final-norm hidden as the MTP draft anchor.
     * Uses an in-place copy into a durable buffer (safe across AutoScope pops).
     * Skips allocation while CUDA-graph buffers are active (copy-only when the
     * buffer already exists from an eager forward).
     *
     * @param hidden post-final-norm hidden {@code [B, S, D]}, {@code [B, 1, D]},
     *               or {@code [B, D]}.
     */
    void capturePreNormHidden(Tensor hidden) {
        if (mtp == null || hidden == null) {
            return;
        }
        Tensor row = hidden;
        boolean sliced = false;
        if (hidden.dim() == 3 && hidden.shape()[1] > 1) {
            try (var last = Index.of(-1)) {
                row = hidden.get(Index.Colon, last);
                sliced = true;
            }
        }
        setLastPreNormHiddenRow(row);
        if (sliced) {
            row.close();
        }
    }

    /**
     * Squeezes an optional size-1 sequence dim then stores/copies {@code row}
     * into {@link #lastPreNormHidden}, matching {@link #capturePreNormHidden}'s
     * graph-mode / dtype / device change handling. Does not close {@code row}
     * itself — the caller owns it.
     *
     * @param row post-final-norm hidden {@code [B, 1, D]} or {@code [B, D]}.
     */
    private void setLastPreNormHiddenRow(Tensor row) {
        Tensor squeezed = row;
        boolean owned = false;
        if (row.dim() == 3 && row.shape()[1] == 1) {
            long[] sh = row.shape();
            squeezed = row.reshape(sh[0], sh[2]);
            owned = true;
        }
        boolean graphMode = kvCachePool != null
                && (kvCachePool.decodeGraphBuffers() || kvCachePool.verifyGraphBuffers());
        boolean needAlloc = lastPreNormHidden == null
                || !java.util.Arrays.equals(lastPreNormHidden.shape(), squeezed.shape())
                || lastPreNormHidden.device().index() != squeezed.device().index()
                || lastPreNormHidden.dtype() != squeezed.dtype();
        if (needAlloc) {
            if (graphMode) {
                // Allocating during CUDA graph capture/replay is illegal; keep
                // the previous anchor until the next eager forward.
                if (owned) {
                    squeezed.close();
                }
                return;
            }
            if (lastPreNormHidden != null) {
                lastPreNormHidden.close();
            }
            lastPreNormHidden = squeezed.copy();
            lastPreNormHidden.detachFromScopes();
        } else {
            smile.torch.Native.copy_(lastPreNormHidden, squeezed);
        }
        if (owned) {
            squeezed.close();
        }
    }

    /**
     * On a partial MTP accept at window position {@code r} (checkpoint-replay
     * path), restores the MTP anchor from the per-position hidden retained in
     * {@link #verifyWindowNormalizedBuf} instead of a second full-window
     * forward.
     *
     * @param r accepted window position (0-indexed).
     */
    public void setMtpAnchorAtWindowPosition(int r) {
        if (mtp == null || verifyWindowNormalizedBuf == null) {
            return;
        }
        try (var idx = Index.of(r); Tensor row = verifyWindowNormalizedBuf.get(Index.Colon, idx)) {
            setLastPreNormHiddenRow(row);
        }
    }

    /**
     * Batched counterpart of {@link #setMtpAnchorAtWindowPosition}: restores
     * each row {@code i}'s MTP anchor from its own retained window position
     * {@code positions[i]} — needed when concurrent requests verified
     * together in one round accept different numbers of draft tokens.
     *
     * @param positions accepted window position per row (0-indexed), length {@code B}.
     */
    public void setMtpAnchorAtWindowPositions(int[] positions) {
        if (mtp == null || verifyWindowNormalizedBuf == null) {
            return;
        }
        int b = positions.length;
        long dim = verifyWindowNormalizedBuf.shape()[2];
        Tensor gathered = Tensor.zeros(
                new Tensor.Options().device(verifyWindowNormalizedBuf.device())
                        .dtype(verifyWindowNormalizedBuf.dtype()).requireGradients(false),
                b, dim);
        try {
            for (int i = 0; i < b; i++) {
                try (var rowIdx = Index.of(i); var posIdx = Index.of(positions[i]);
                     Tensor src = verifyWindowNormalizedBuf.get(rowIdx, posIdx)) {
                    gathered.put_(src, Index.of(i), Index.Colon);
                }
            }
            setLastPreNormHiddenRow(gathered);
        } finally {
            gathered.close();
        }
    }

    /** Allocates the retained per-position verify-window hidden buffer (shape/device/dtype change only). */
    private void ensureVerifyWindowNormalizedBuf(Tensor prototype) {
        if (verifyWindowNormalizedBuf != null
                && java.util.Arrays.equals(verifyWindowNormalizedBuf.shape(), prototype.shape())) {
            return;
        }
        if (verifyWindowNormalizedBuf != null) {
            verifyWindowNormalizedBuf.close();
            verifyWindowNormalizedBuf = null;
        }
        verifyWindowNormalizedBuf = Tensor.zeros(
                new Tensor.Options().device(prototype.device()).dtype(prototype.dtype()),
                prototype.shape());
        verifyWindowNormalizedBuf.detachFromScopes();
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
        boolean profile = DecodeForwardProfile.enabled();
        try (var pos = Index.slice(startPos, startPos + seqlen)) {
            long tEmbed = profile ? System.nanoTime() : 0L;
            Tensor h = tokEmbeddings.forward(tokens);
            if (profile) {
                DecodeForwardProfile.addEmbed(System.nanoTime() - tEmbed);
            }
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
            if (mtp != null) {
                // MTP expects the backbone hidden that feeds the LM head (post-final-norm),
                // matching vLLM/SGLang Qwen3.5 MTP.
                capturePreNormHidden(normalized);
                if (deltaNetStatePool != null && deltaNetStatePool.verifyWindowActive()) {
                    ensureVerifyWindowNormalizedBuf(normalized);
                    smile.torch.Native.copy_(verifyWindowNormalizedBuf, normalized);
                }
            }
            // mask is independently allocated; free before the vocab-sized lm_head.
            if (mask != null) {
                mask.close();
                mask = null;
            }
            // cos/sin are slices of long-lived tables — leave to AutoScope pop.
            long tHead = profile ? System.nanoTime() : 0L;
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
            if (profile) {
                DecodeForwardProfile.addLmHead(System.nanoTime() - tHead);
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
            if (mtp != null) {
                capturePreNormHidden(normalized);
            }
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
        // Do not wrap an AutoScope here: forwardRaggedDecodeCore already scopes
        // intermediates and promoteToParent()s logits to the caller. An outer
        // push/pop would free those logits before the engine can read them
        // (SIGSEGV in logitsRowFromDecodeOutput under B>1 / multi-request).
        Device device = tokens.device();
        long freeBefore = cudaFreeBytes(device);
        Tensor cos = PartialRotaryEncoding.gather(rope.cos(), ropePositions);
        Tensor sin = PartialRotaryEncoding.gather(rope.sin(), ropePositions);
        try {
            return forwardRaggedDecodeCore(tokens, cachePositions, cos, sin);
        } finally {
            cos.close();
            sin.close();
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
     * Uniform decode forward with optional CUDA graph capture / replay.
     *
     * @param tokens         token ids {@code [B, 1]}.
     * @param cachePositions KV write positions (uniform length across rows).
     * @param ropePositions  RoPE table gather positions.
     * @return logits in float32 {@code [B, 1, V]} (graph path returns persistent buffer).
     */
    public Tensor forwardDecodeGraph(Tensor tokens, int[] cachePositions, int[] ropePositions) {
        if (!DecodeCudaGraph.enabled() || kvCachePool == null) {
            return forward(tokens, cachePositions, ropePositions, false);
        }
        int batch = (int) tokens.shape()[0];
        if (!DecodeCudaGraph.canGraphDecode(cachePositions)) {
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
        promotePrefetchIfReady(batch, numPages);

        kvCachePool.setDecodeGraphBuffers(true);
        try {
            ensureDecodeGraphTokenBuf(tokens.device(), batch, tokens.dtype());
            smile.torch.Native.copy_(decodeGraphTokenBuf, tokens);
            ensureDecodeGraphRoPEBuffers(tokens.device(), batch);
            prepareDecodeGraphInputs(cachePositions, ropePositions, cacheLen);

            if (decodeGraphSession.canReplay(batch, numPages)) {
                decodeGraphSession.replay(tpRank);
                DecodeCudaGraph.markPersistentLogits(true);
                recordDecodeGraphContext(batch, numPages, cacheLen, cachePositions, ropePositions);
                maybePrefetchNextBucket(batch, numPages, cacheLen, cachePositions, ropePositions,
                        tokens.device());
                return decodeGraphLogitsBuf;
            }

            boolean capture = decodeGraphSession.shouldCapture(batch, numPages, tpRank);
            if (capture) {
                if (decodeGraphLogitsBuf == null) {
                    throw new IllegalStateException(
                            "decode graph logits buffer missing; warmup must run before capture");
                }
                int deviceIndex = Byte.toUnsignedInt(tokens.device().index());
                try {
                    decodeGraphSession.beginCapture(deviceIndex);
                    try {
                        Tensor raw = forwardRaggedDecodeCore(
                                decodeGraphTokenBuf, cachePositions,
                                decodeGraphCosBuf, decodeGraphSinBuf);
                        smile.torch.Native.copy_(decodeGraphLogitsBuf, raw);
                        decodeGraphLogitsOut = decodeGraphLogitsBuf;
                    } finally {
                        decodeGraphSession.endCapture();
                    }
                    if (decodeGraphSession.canReplay(batch, numPages)) {
                        DecodeCudaGraphLog.bucketCapture(tpRank, batch, numPages,
                                decodeGraphSession.lastCaptureMs(), false);
                        // capture_end() only instantiates the graph; it never executes the
                        // recorded operations (see smile_cuda_graph.cpp: "capture_end()
                        // instantiates; do not call instantiate()"). Without this replay,
                        // decodeGraphLogitsBuf still held whatever was there BEFORE this round
                        // (stale/garbage) — same bug found and fixed in forwardVerifyGraph's
                        // identical capture branch; the capture round must explicitly replay
                        // once to actually produce this round's real result.
                        decodeGraphSession.replay(tpRank);
                        DecodeCudaGraph.markPersistentLogits(true);
                        maybePrefetchNextBucket(batch, numPages, cacheLen, cachePositions,
                                ropePositions, tokens.device());
                        return decodeGraphLogitsBuf;
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

            Tensor raw = forwardRaggedDecodeCore(
                    decodeGraphTokenBuf, cachePositions, decodeGraphCosBuf, decodeGraphSinBuf);
            ensureDecodeGraphLogitsBuf(raw);
            smile.torch.Native.copy_(decodeGraphLogitsBuf, raw);
            return raw;
        } finally {
            kvCachePool.setDecodeGraphBuffers(false);
        }
    }

    private void recordDecodeGraphContext(int batch, int numPages, int cacheLen,
                                          int[] cachePositions, int[] ropePositions) {
        lastDecodeGraphBatch = batch;
        lastDecodeGraphNumPages = numPages;
        lastDecodeGraphCacheLen = cacheLen;
        lastDecodeGraphCachePos = cachePositions[0];
        lastDecodeGraphRopePos = ropePositions.clone();
    }

    /** Continues next-bucket prefetch when the scheduler is idle but KV remains bound. */
    void idleAdvancePrefetch() {
        if (!DecodeCudaGraph.preCaptureEnabled() || kvCachePool == null
                || lastDecodeGraphBatch <= 0 || lastDecodeGraphRopePos == null) {
            return;
        }
        if (kvCachePool.boundRequestCount() == 0) {
            return;
        }
        Device device = kvCachePool.device();
        maybePrefetchNextBucket(lastDecodeGraphBatch, lastDecodeGraphNumPages,
                lastDecodeGraphCacheLen,
                new int[]{lastDecodeGraphCachePos},
                lastDecodeGraphRopePos,
                device);
    }

    private void promotePrefetchIfReady(int batch, int numPages) {
        if (decodeGraphPrefetchSession == null
                || !decodeGraphPrefetchSession.canReplay(batch, numPages)) {
            return;
        }
        DecodeCudaGraphLog.prefetchHit(tpRank, batch, numPages);
        if (prefetchedStepMeta != null && prefetchedStepMetaLen > 0) {
            kvCachePool.installPrefetchedStepMetadata(prefetchedStepMeta, prefetchedStepMetaLen);
        }
        if (decodeGraphSession != null) {
            decodeGraphSession.close();
        }
        decodeGraphSession = decodeGraphPrefetchSession;
        decodeGraphPrefetchSession = null;
        prefetchTargetBatch = -1;
        prefetchTargetNumPages = -1;
        prefetchedStepMeta = null;
        prefetchedStepMetaLen = -1;
    }

    private void maybePrefetchNextBucket(int batch, int numPages, int cacheLen,
                                           int[] cachePositions, int[] ropePositions,
                                           Device device) {
        if (!DecodeCudaGraph.preCaptureEnabled()) {
            return;
        }
        long freeBytes = cudaFreeBytes(device);
        if (!DecodeCudaGraph.hasPrefetchHeadroom(freeBytes)) {
            DecodeCudaGraph.logPrefetchSkippedLowMemory(tpRank, freeBytes);
            return;
        }
        int stepsUntil = kvCachePool.stepsUntilPageBoundary(cacheLen);
        int lead = DecodeCudaGraph.prefetchLeadSteps();
        if (stepsUntil <= 0 || stepsUntil > lead) {
            return;
        }
        int nextPages = numPages + 1;
        int nextCacheLen = kvCachePool.firstCacheLenInNextPageBucket(numPages);
        if (nextCacheLen > kvCachePool.requestCapacity()) {
            return;
        }
        if (decodeGraphPrefetchSession != null
                && decodeGraphPrefetchSession.canReplay(batch, nextPages)) {
            return;
        }
        if (prefetchTargetNumPages != nextPages || prefetchTargetBatch != batch) {
            resetPrefetchSession(batch, nextPages);
        }
        if (decodeGraphPrefetchSession == null) {
            return;
        }
        int nextCachePos = nextCacheLen - 1;
        int posDelta = nextCachePos - cachePositions[0];
        int[] prefetchCachePos = new int[batch];
        int[] prefetchRopePos = new int[batch];
        Arrays.fill(prefetchCachePos, nextCachePos);
        for (int i = 0; i < batch; i++) {
            prefetchRopePos[i] = ropePositions[i] + posDelta;
        }
        runPrefetchStep(batch, nextPages, nextCacheLen, prefetchCachePos, prefetchRopePos, device);
    }

    private void resetPrefetchSession(int batch, int numPages) {
        if (decodeGraphPrefetchSession != null) {
            decodeGraphPrefetchSession.close();
        }
        decodeGraphPrefetchSession = DecodeCudaGraphSession.tryCreate();
        prefetchTargetBatch = batch;
        prefetchTargetNumPages = numPages;
        DecodeCudaGraphLog.prefetchStart(tpRank, batch, numPages);
    }

    private void runPrefetchStep(int batch, int nextPages, int nextCacheLen,
                                   int[] prefetchCachePos, int[] prefetchRopePos,
                                   Device device) {
        try {
            Runnable work = () -> runPrefetchStepInner(batch, nextPages, nextCacheLen,
                    prefetchCachePos, prefetchRopePos, device);
            if (deltaNetStatePool != null) {
                deltaNetStatePool.withPreservedActive(work);
            } else {
                work.run();
            }
        } catch (RuntimeException e) {
            logger.warn("tpRank={}: decode CUDA graph prefetch failed, disabling pre-capture: {}",
                    tpRank, e.getMessage());
            DecodeCudaGraph.disablePreCapture(e.getMessage());
            if (decodeGraphPrefetchSession != null) {
                decodeGraphPrefetchSession.close();
                decodeGraphPrefetchSession = null;
            }
            prefetchTargetBatch = -1;
            prefetchTargetNumPages = -1;
            prefetchedStepMeta = null;
            prefetchedStepMetaLen = -1;
            // OOM during prefetch often leaves the caching allocator fragmented;
            // return cached blocks so the live decode path can continue.
            try {
                device.emptyCache();
            } catch (RuntimeException ignored) {
                // best-effort
            }
        }
    }

    private void runPrefetchStepInner(int batch, int nextPages, int nextCacheLen,
                                      int[] prefetchCachePos, int[] prefetchRopePos,
                                      Device device) {
        kvCachePool.setDecodeGraphBuffers(true);
        try {
            ensureDecodeGraphRoPEBuffers(device, batch);
            kvCachePool.beginPrefetchDecodeGraphStep(nextCacheLen, prefetchCachePos[0], batch);
            FlashInferKvMetadata capturedMeta = null;
            try {
                PartialRotaryEncoding.gatherInto(rope.cos(), prefetchRopePos, decodeGraphCosBuf);
                PartialRotaryEncoding.gatherInto(rope.sin(), prefetchRopePos, decodeGraphSinBuf);
                boolean capture = decodeGraphPrefetchSession.shouldCapture(
                        batch, nextPages, tpRank, true);
                if (capture) {
                    if (decodeGraphLogitsBuf == null) {
                        return;
                    }
                    capturedMeta = kvCachePool.currentStepFlashInferMetadata();
                    int deviceIndex = Byte.toUnsignedInt(device.index());
                    decodeGraphPrefetchSession.beginCapture(deviceIndex);
                    try {
                        Tensor raw = forwardRaggedDecodeCore(
                                decodeGraphTokenBuf, prefetchCachePos,
                                decodeGraphCosBuf, decodeGraphSinBuf);
                        smile.torch.Native.copy_(decodeGraphLogitsBuf, raw);
                    } finally {
                        decodeGraphPrefetchSession.endCapture();
                    }
                    if (decodeGraphPrefetchSession.canReplay(batch, nextPages)) {
                        DecodeCudaGraphLog.prefetchReady(tpRank, batch, nextPages,
                                decodeGraphPrefetchSession.lastCaptureMs());
                        if (capturedMeta != null) {
                            prefetchedStepMeta = capturedMeta;
                            prefetchedStepMetaLen = nextCacheLen;
                        }
                    }
                } else {
                    Tensor raw = forwardRaggedDecodeCore(
                            decodeGraphTokenBuf, prefetchCachePos,
                            decodeGraphCosBuf, decodeGraphSinBuf);
                    ensureDecodeGraphLogitsBuf(raw);
                    smile.torch.Native.copy_(decodeGraphLogitsBuf, raw);
                }
            } finally {
                kvCachePool.endPrefetchDecodeGraphStep();
            }
        } finally {
            kvCachePool.setDecodeGraphBuffers(false);
        }
    }

    /** Releases CUDA graph resources for this rank. */
    public void closeDecodeGraph() {
        invalidateDecodeCudaGraphs();
        if (decodeGraphCosBuf != null) {
            decodeGraphCosBuf.close();
            decodeGraphCosBuf = null;
        }
        if (decodeGraphSinBuf != null) {
            decodeGraphSinBuf.close();
            decodeGraphSinBuf = null;
        }
        if (decodeGraphTokenBuf != null) {
            decodeGraphTokenBuf.close();
            decodeGraphTokenBuf = null;
        }
        if (decodeGraphLogitsBuf != null) {
            decodeGraphLogitsBuf.close();
            decodeGraphLogitsBuf = null;
        }
        decodeGraphLogitsOut = null;
    }

    /**
     * Drops captured decode / prefetch CUDA graphs while keeping durable
     * token/RoPE/logits buffers. Required after MTP window verify or
     * {@link KvCachePool#truncateTo}, which rebuild FlashInfer CSR tensors that
     * a captured graph may still reference (replay → illegal memory access).
     */
    public void invalidateDecodeCudaGraphs() {
        if (decodeGraphPrefetchSession != null) {
            decodeGraphPrefetchSession.close();
            decodeGraphPrefetchSession = null;
        }
        prefetchTargetBatch = -1;
        prefetchTargetNumPages = -1;
        prefetchedStepMeta = null;
        prefetchedStepMetaLen = -1;
        if (decodeGraphSession != null) {
            decodeGraphSession.close();
            decodeGraphSession = null;
        }
        lastDecodeGraphBatch = -1;
        lastDecodeGraphNumPages = -1;
        lastDecodeGraphCacheLen = -1;
        lastDecodeGraphCachePos = -1;
        lastDecodeGraphRopePos = null;
    }

    private void ensureDecodeGraphTokenBuf(Device device, int batch, ScalarType dtype) {
        if (decodeGraphTokenBuf != null
                && decodeGraphTokenBuf.shape()[0] == batch
                && decodeGraphTokenBuf.dtype() == dtype) {
            return;
        }
        if (decodeGraphTokenBuf != null) {
            decodeGraphTokenBuf.close();
            decodeGraphTokenBuf = null;
        }
        decodeGraphTokenBuf = Tensor.zeros(
                new Tensor.Options().device(device).dtype(dtype), batch, 1);
        decodeGraphTokenBuf.detachFromScopes();
    }

    private void ensureDecodeGraphRoPEBuffers(Device device, int batch) {
        if (decodeGraphCosBuf != null && decodeGraphCosBuf.shape()[0] == batch) {
            return;
        }
        if (decodeGraphCosBuf != null) {
            decodeGraphCosBuf.close();
            decodeGraphCosBuf = null;
        }
        if (decodeGraphSinBuf != null) {
            decodeGraphSinBuf.close();
            decodeGraphSinBuf = null;
        }
        int rotaryDim = params.rotaryDim();
        var opts = new Tensor.Options().device(device).dtype(ScalarType.Float);
        decodeGraphCosBuf = Tensor.zeros(opts, batch, 1, rotaryDim);
        decodeGraphSinBuf = Tensor.zeros(opts, batch, 1, rotaryDim);
        decodeGraphCosBuf.detachFromScopes();
        decodeGraphSinBuf.detachFromScopes();
    }

    /** Allocates a stable logits buffer before CUDA graph capture (warmup only). */
    private void ensureDecodeGraphLogitsBuf(Tensor prototype) {
        if (decodeGraphLogitsBuf != null
                && java.util.Arrays.equals(decodeGraphLogitsBuf.shape(), prototype.shape())) {
            return;
        }
        if (decodeGraphLogitsBuf != null) {
            decodeGraphLogitsBuf.close();
            decodeGraphLogitsBuf = null;
        }
        decodeGraphLogitsBuf = Tensor.zeros(
                new Tensor.Options().device(prototype.device()).dtype(prototype.dtype()),
                prototype.shape());
        decodeGraphLogitsBuf.detachFromScopes();
    }

    private void prepareDecodeGraphInputs(int[] cachePositions, int[] ropePositions, int cacheLen) {
        PartialRotaryEncoding.gatherInto(rope.cos(), ropePositions, decodeGraphCosBuf);
        PartialRotaryEncoding.gatherInto(rope.sin(), ropePositions, decodeGraphSinBuf);
        kvCachePool.prepareDecodeGraphStep(cacheLen, cachePositions);
    }

    private Tensor forwardRaggedDecodeCore(Tensor tokens, int[] cachePositions,
                                           Tensor cos, Tensor sin) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            boolean profile = DecodeForwardProfile.enabled();
            long tEmbed = profile ? System.nanoTime() : 0L;
            Tensor h = tokEmbeddings.forward(tokens);
            if (profile) {
                DecodeForwardProfile.addEmbed(System.nanoTime() - tEmbed);
            }
            for (int i = 0; i < layers.size(); i++) {
                Tensor next = layers.get(i).forward(h, cachePositions, cos, sin, null);
                h.close();
                h = next;
            }
            long tHead = profile ? System.nanoTime() : 0L;
            Tensor normalized = norm.forward(h);
            h.close();
            if (mtp != null) {
                capturePreNormHidden(normalized);
            }
            Tensor logitsF = lmHead.forward(normalized);
            normalized.close();
            Tensor logits = logitsF.to(ScalarType.Float);
            if (logits != logitsF) {
                logitsF.close();
            }
            if (profile) {
                DecodeForwardProfile.addLmHead(System.nanoTime() - tHead);
            }
            logits.promoteToParent();
            return logits;
        } finally {
            Tensor.pop();
        }
    }

    /**
     * Uniform MTP window-verify forward (query length {@code S = numDrafts + 1},
     * every batch row sharing the same start position) with optional CUDA
     * graph capture / replay.
     *
     * <p>Stage 4 shadow-run: whenever {@link VerifyCudaGraph#enabled()}, every
     * call runs eager through the new stable-buffer / graph-capturable kernel
     * path (see {@link smile.llm.attention.AttentionContext} dispatch in
     * {@code GatedAttention.forwardUniform}), and bucket/warmup bookkeeping in
     * {@link #verifyGraphSession} runs for real against live traffic shapes —
     * but {@link VerifyCudaGraph#captureEnabled()} is {@code false} until
     * Stage 5, so {@code beginCapture} is never reached. This lets the
     * existing eager {@link #forward(Tensor, int, boolean)} path serve as the
     * numeric go/no-go reference for the new kernel/buffer plumbing before
     * Stage 5 adds the (separate) capture/replay risk.
     *
     * @param tokens   token ids {@code [B, S]}.
     * @param startPos cache start position (uniform across the batch).
     * @return logits in float32 {@code [B, S, V]} (graph path returns persistent buffer).
     */
    public Tensor forwardVerifyGraph(Tensor tokens, int startPos) {
        // The device check matters beyond the obvious: SMILE_VERIFY_CUDA_GRAPH=1 is set
        // for the whole :deep test module (Stage 1/2's GPU-only tests self-skip via
        // assumeTrue(cudaAvailable())), so VerifyCudaGraph.enabled() alone (native
        // symbol linked + env var) is not sufficient to keep this dormant on the
        // CPU-only QwenWindowVerifyTest suite — this must never reach
        // VerifyCudaGraphSession.tryCreate() -> Native.cudaGraphCreate() without an
        // actual CUDA device.
        if (!VerifyCudaGraph.enabled() || kvCachePool == null || !tokens.device().isCUDA()
                || AttentionBackends.current() != AttentionBackend.FLASHINFER) {
            return forward(tokens, startPos, true);
        }
        int batch = (int) tokens.shape()[0];
        int windowLen = (int) tokens.shape()[1];
        int[] startPositions = new int[batch];
        Arrays.fill(startPositions, startPos);
        if (!VerifyCudaGraph.canGraphVerify(startPositions)) {
            return forward(tokens, startPos, true);
        }
        if (verifyGraphSession == null) {
            verifyGraphSession = VerifyCudaGraphSession.tryCreate();
        }
        if (verifyGraphSession == null) {
            return forward(tokens, startPos, true);
        }

        int cacheLen = startPos + windowLen;
        int numPages = kvCachePool.numPagesForLength(cacheLen);

        kvCachePool.setVerifyGraphBuffers(true);
        try {
            ensureVerifyGraphTokenBuf(tokens.device(), batch, windowLen, tokens.dtype());
            smile.torch.Native.copy_(verifyGraphTokenBuf, tokens);
            ensureVerifyGraphRoPEBuffers(tokens.device(), windowLen);
            prepareVerifyGraphInputs(startPos, windowLen, cacheLen, batch);

            if (verifyGraphSession.canReplay(batch, windowLen, numPages)) {
                verifyGraphSession.replay(tpRank);
                VerifyCudaGraph.markPersistentLogits(true);
                return verifyGraphLogitsBuf;
            }

            boolean shouldCaptureNow =
                    verifyGraphSession.shouldCapture(batch, windowLen, numPages, tpRank);
            if (VerifyCudaGraph.captureEnabled() && shouldCaptureNow) {
                if (verifyGraphLogitsBuf == null) {
                    throw new IllegalStateException(
                            "verify graph logits buffer missing; warmup must run before capture");
                }
                int deviceIndex = Byte.toUnsignedInt(tokens.device().index());
                // NOT serialized across TP ranks: capture must let all ranks reach
                // their own NCCL all-reduce concurrently, exactly like decode's own
                // proven-working graph — serializing rank 0's entire capture
                // (including its own all-reduce call) ahead of ranks 1-3 starting
                // theirs fights that requirement rather than helping.
                try {
                    verifyGraphSession.beginCapture(deviceIndex);
                    try {
                        Tensor raw = forwardVerifyGraphCore(
                                verifyGraphTokenBuf, startPositions,
                                verifyGraphCosBuf, verifyGraphSinBuf, null);
                        smile.torch.Native.copy_(verifyGraphLogitsBuf, raw);
                        verifyGraphLogitsOut = verifyGraphLogitsBuf;
                    } finally {
                        verifyGraphSession.endCapture();
                    }
                    if (verifyGraphSession.canReplay(batch, windowLen, numPages)) {
                        verifyGraphSession.logCapture(tpRank);
                        // capture_end() only instantiates the graph; it never executes the
                        // recorded operations (confirmed in smile_cuda_graph.cpp: "capture_end()
                        // instantiates; do not call instantiate()"). Without this replay,
                        // verifyGraphLogitsBuf still held whatever was there BEFORE this round
                        // (stale/garbage) — the capture round must explicitly replay once to
                        // actually produce this round's real result.
                        verifyGraphSession.replay(tpRank);
                        VerifyCudaGraph.markPersistentLogits(true);
                        return verifyGraphLogitsBuf;
                    }
                    logger.warn("tpRank={}: verify CUDA graph capture did not produce a "
                            + "replayable graph", tpRank);
                    VerifyCudaGraph.disableCapture("capture incomplete");
                    VerifyCudaGraph.markPersistentLogits(false);
                    verifyGraphSession.close();
                    verifyGraphSession = null;
                    verifyGraphLogitsOut = null;
                } catch (RuntimeException e) {
                    logger.warn("tpRank={}: verify CUDA graph capture failed, falling back "
                            + "to eager: {}", tpRank, e.getMessage());
                    VerifyCudaGraph.disableCapture(e.getMessage());
                    VerifyCudaGraph.markPersistentLogits(false);
                    if (verifyGraphSession != null) {
                        verifyGraphSession.close();
                        verifyGraphSession = null;
                    }
                    verifyGraphLogitsOut = null;
                    kvCachePool.setVerifyGraphBuffers(false);
                    return forward(tokens, startPos, true);
                }
            }

            Tensor raw = forwardVerifyGraphCore(
                    verifyGraphTokenBuf, startPositions, verifyGraphCosBuf, verifyGraphSinBuf, null);
            ensureVerifyGraphLogitsBuf(raw);
            smile.torch.Native.copy_(verifyGraphLogitsBuf, raw);
            return raw;
        } finally {
            kvCachePool.setVerifyGraphBuffers(false);
        }
    }

    /** Releases verify CUDA graph resources for this rank. */
    public void closeVerifyGraph() {
        invalidateVerifyCudaGraphs();
        if (verifyGraphCosBuf != null) {
            verifyGraphCosBuf.close();
            verifyGraphCosBuf = null;
        }
        if (verifyGraphSinBuf != null) {
            verifyGraphSinBuf.close();
            verifyGraphSinBuf = null;
        }
        if (verifyGraphTokenBuf != null) {
            verifyGraphTokenBuf.close();
            verifyGraphTokenBuf = null;
        }
        if (verifyGraphLogitsBuf != null) {
            verifyGraphLogitsBuf.close();
            verifyGraphLogitsBuf = null;
        }
        verifyGraphLogitsOut = null;
    }

    /**
     * Drops a captured verify CUDA graph while keeping durable token/RoPE/
     * logits buffers. Required after {@link KvCachePool#truncateTo} rebuilds
     * (not bumps-in-place) the FlashInfer CSR tensors a captured graph may
     * still reference (replay &rarr; illegal memory access).
     */
    public void invalidateVerifyCudaGraphs() {
        if (verifyGraphSession != null) {
            verifyGraphSession.close();
            verifyGraphSession = null;
        }
    }

    private void ensureVerifyGraphTokenBuf(Device device, int batch, int windowLen, ScalarType dtype) {
        if (verifyGraphTokenBuf != null
                && verifyGraphTokenBuf.shape()[0] == batch
                && verifyGraphTokenBuf.shape()[1] == windowLen
                && verifyGraphTokenBuf.dtype() == dtype) {
            return;
        }
        if (verifyGraphTokenBuf != null) {
            verifyGraphTokenBuf.close();
            verifyGraphTokenBuf = null;
        }
        verifyGraphTokenBuf = Tensor.zeros(
                new Tensor.Options().device(device).dtype(dtype), batch, windowLen);
        verifyGraphTokenBuf.detachFromScopes();
    }

    private void ensureVerifyGraphRoPEBuffers(Device device, int windowLen) {
        if (verifyGraphCosBuf != null && verifyGraphCosBuf.shape()[0] == windowLen) {
            return;
        }
        if (verifyGraphCosBuf != null) {
            verifyGraphCosBuf.close();
            verifyGraphCosBuf = null;
        }
        if (verifyGraphSinBuf != null) {
            verifyGraphSinBuf.close();
            verifyGraphSinBuf = null;
        }
        int rotaryDim = params.rotaryDim();
        var opts = new Tensor.Options().device(device).dtype(ScalarType.Float);
        verifyGraphCosBuf = Tensor.zeros(opts, windowLen, rotaryDim);
        verifyGraphSinBuf = Tensor.zeros(opts, windowLen, rotaryDim);
        verifyGraphCosBuf.detachFromScopes();
        verifyGraphSinBuf.detachFromScopes();
    }

    /** Allocates a stable logits buffer before CUDA graph capture (warmup only). */
    private void ensureVerifyGraphLogitsBuf(Tensor prototype) {
        if (verifyGraphLogitsBuf != null
                && java.util.Arrays.equals(verifyGraphLogitsBuf.shape(), prototype.shape())) {
            return;
        }
        if (verifyGraphLogitsBuf != null) {
            verifyGraphLogitsBuf.close();
            verifyGraphLogitsBuf = null;
        }
        verifyGraphLogitsBuf = Tensor.zeros(
                new Tensor.Options().device(prototype.device()).dtype(prototype.dtype()),
                prototype.shape());
        verifyGraphLogitsBuf.detachFromScopes();
    }

    private void prepareVerifyGraphInputs(int startPos, int windowLen, int cacheLen, int batch) {
        PartialRotaryEncoding.gatherWindowInto(rope.cos(), startPos, windowLen, verifyGraphCosBuf);
        PartialRotaryEncoding.gatherWindowInto(rope.sin(), startPos, windowLen, verifyGraphSinBuf);
        int[] startPositions = new int[batch];
        Arrays.fill(startPositions, startPos);
        kvCachePool.prepareVerifyGraphStep(cacheLen, startPositions, windowLen);
    }

    /**
     * Verify-graph forward core: identical structure to {@link #forwardRaggedDecodeCore}
     * but always scores every window position (the whole point of window verify —
     * see {@link #forward(Tensor, int, boolean)}'s {@code allTokenLogits} branch),
     * so unlike decode's {@code S == 1} core, no last-row slicing is needed.
     *
     * @param mask explicit dense causal mask, or {@code null} to rely on the
     *             FlashInfer kernel's own {@code MaskMode::kCausal} — <b>only
     *             correct when the caller has set {@code kvCachePool.setVerifyGraphBuffers(true)}
     *             first</b> (every existing call site does; see {@link #forwardVerifyGraph}).
     *             Pass an explicit mask instead for any caller that does not set that flag
     *             (e.g. {@link #forwardBatchedVerify}), or attention silently runs
     *             non-causal within the window.
     */
    private Tensor forwardVerifyGraphCore(Tensor tokens, int[] startPositions,
                                          Tensor cos, Tensor sin, Tensor mask) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        boolean profile = DecodeForwardProfile.enabled();
        try {
            long tEmbed = profile ? System.nanoTime() : 0L;
            Tensor h = tokEmbeddings.forward(tokens);
            if (profile) {
                DecodeForwardProfile.addEmbed(System.nanoTime() - tEmbed);
            }
            Tensor maskUse = mask != null && mask.dtype() != h.dtype() ? mask.to(h.dtype()) : mask;
            for (int i = 0; i < layers.size(); i++) {
                Tensor next = layers.get(i).forward(h, startPositions, cos, sin, maskUse);
                h.close();
                h = next;
            }
            if (maskUse != null && maskUse != mask) {
                maskUse.close();
            }
            Tensor normalized = norm.forward(h);
            h.close();
            if (mtp != null) {
                capturePreNormHidden(normalized);
                if (deltaNetStatePool != null && deltaNetStatePool.verifyWindowActive()) {
                    ensureVerifyWindowNormalizedBuf(normalized);
                    smile.torch.Native.copy_(verifyWindowNormalizedBuf, normalized);
                }
            }
            long tHead = profile ? System.nanoTime() : 0L;
            Tensor logitsF = lmHead.forward(normalized);
            normalized.close();
            if (profile) {
                DecodeForwardProfile.addLmHead(System.nanoTime() - tHead);
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
     * Batched eager verify forward for a cohort of concurrent requests that
     * may be at <em>different</em> absolute positions — the multi-request
     * counterpart of {@link #forwardVerifyGraph}, which requires a single
     * shared start position across the batch so it can be CUDA-graph-captured
     * (see {@code VerifyCudaGraph#canGraphVerify}). This always runs eager;
     * heterogeneous-position batched cohorts do not graph-capture (a
     * deliberately deferred fast-follow — see the batching plan).
     *
     * @param tokens         token ids {@code [B, windowLen]}.
     * @param startPositions KV write position of the window's first token per row.
     * @param cacheLengths   inclusive cache length per row (prompt + already-sealed generation).
     * @return logits {@code [B, windowLen, V]} in float32.
     */
    public Tensor forwardBatchedVerify(Tensor tokens, int[] startPositions, int[] cacheLengths) {
        if (kvCachePool == null) {
            throw new IllegalStateException("KV cache pool not installed");
        }
        int windowLen = (int) tokens.shape()[1];
        kvCachePool.prepareVerifyGraphStep(cacheLengths, startPositions, windowLen);
        Tensor cos = PartialRotaryEncoding.gatherWindow(rope.cos(), startPositions, windowLen);
        Tensor sin = PartialRotaryEncoding.gatherWindow(rope.sin(), startPositions, windowLen);
        // forwardVerifyGraphCore relies on the FlashInfer kernel's own
        // MaskMode::kCausal when the caller sets kvCachePool.verifyGraphBuffers(true)
        // (every other caller does); this one doesn't (that flag also gates
        // lastPreNormHidden reallocation, which a first-ever batch-size-B round
        // here legitimately needs), so build the same explicit dense causal
        // mask QwenModel.forward's own S>1 path uses instead — correct under
        // every backend, not just FlashInfer's kernel-internal mode.
        int startPos = startPositions[0];
        Tensor mask = null;
        if (windowLen > 1) {
            var maskOpts = new Tensor.Options()
                    .device(tokens.device()).dtype(ScalarType.Float).requireGradients(false);
            mask = Tensor.zeros(maskOpts, windowLen, windowLen).fill_(Float.NEGATIVE_INFINITY);
            mask.triu_(1);
            if (startPos > 0) {
                try (Tensor zeros = Tensor.zeros(maskOpts, windowLen, startPos)) {
                    Tensor prev = mask;
                    mask = Tensor.hstack(zeros, prev);
                    prev.close();
                }
            }
        }
        try {
            return forwardVerifyGraphCore(tokens, startPositions, cos, sin, mask);
        } finally {
            cos.close();
            sin.close();
            if (mask != null) {
                mask.close();
            }
        }
    }

    @Override
    public Tensor forward(Tensor tokens) {
        return forward(tokens, 0);
    }
}
