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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Tensor;
import smile.llm.attention.Attention;
import smile.llm.attention.AttentionContext;
import smile.llm.attention.TorchNativeAttentionKernel;
import smile.llm.cache.KvCachePool;
import smile.llm.model.qwen.PartialRotaryEncoding;
import smile.llm.model.qwen.QwenRMSNorm;
import smile.llm.quant.LinearOp;
import smile.llm.quant.MxFp8Layer;
import smile.torch.Native;
import smile.util.AutoScope;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_module;

/**
 * MiniMax-M3 grouped-query attention: per-head Gemma QK norm, partial RoPE, no output gate.
 *
 * <p>On MSA layers the index projections ({@code index_q_proj}, {@code index_k_proj} and their
 * norms) are registered so the checkpoint loads, but this reference implementation attends over
 * the full causal history (dense). With {@code topk_blocks} covering every visible block, MSA
 * selects all blocks and equals dense attention, so short prompts are exact; block selection
 * lands in the sparse-attention phase.
 *
 * <p>Uses the torch-native SDPA kernel over the paged KV pool; mixed positions are supported
 * for single-token decode through a per-row additive mask.
 *
 * @author Haifeng Li
 */
public class MiniMaxAttention implements Attention {
    private static final TorchNativeAttentionKernel KERNEL = new TorchNativeAttentionKernel();

    final MemorySegment module;
    final int numHeads;
    final int numKvHeads;
    final int numRep;
    final int headDim;
    final int rotaryDim;
    final int kvLayerId;
    LinearOp qProj, kProj, vProj, oProj;
    final QwenRMSNorm qNorm, kNorm;
    final LinearOp indexQProj, indexKProj;
    final QwenRMSNorm indexQNorm, indexKNorm;
    KvCachePool cachePool;
    /** q|k|v (|index_q|index_k on MSA layers) as one GEMM, or {@code null}. */
    private LinearOp fusedProj;
    /** MSA parameters; {@code null} on dense layers. */
    final MiniMaxModelArgs args;
    /** Index-key cache and this layer's ordinal in it; {@code null} until installed. */
    MiniMaxIndexCache indexCache;
    int indexOrdinal;
    /** Forces dense attention on MSA layers (for tests and debugging). */
    boolean forceDense;

    /**
     * Constructor.
     *
     * @param args    model hyperparameters.
     * @param layerId decoder layer index (also the KV pool layer).
     */
    public MiniMaxAttention(MiniMaxModelArgs args, int layerId) {
        if (args.numHeads() % args.numKvHeads() != 0) {
            throw new IllegalArgumentException("numHeads must be divisible by numKvHeads");
        }
        this.numHeads = args.numHeads();
        this.numKvHeads = args.numKvHeads();
        this.numRep = numHeads / numKvHeads;
        this.headDim = args.headDim();
        this.rotaryDim = args.rotaryDim();
        this.kvLayerId = layerId;
        this.args = args.isSparse(layerId) ? args : null;
        int dim = args.dim();
        MiniMaxWeightMode mode = args.weightMode();
        this.qProj = mode.newLinear(dim, numHeads * headDim);
        this.kProj = mode.newLinear(dim, numKvHeads * headDim);
        this.vProj = mode.newLinear(dim, numKvHeads * headDim);
        this.oProj = mode.newLinear(numHeads * headDim, dim);
        this.qNorm = new QwenRMSNorm(headDim, args.normEps());
        this.kNorm = new QwenRMSNorm(headDim, args.normEps());

        boolean sparse = args.isSparse(layerId);
        int indexWidth = args.numIndexHeads() * args.indexDim();
        this.indexQProj = sparse ? mode.newLinear(dim, indexWidth) : null;
        this.indexKProj = sparse ? mode.newLinear(dim, args.numIndexKeyHeads() * args.indexDim()) : null;
        this.indexQNorm = sparse ? new QwenRMSNorm(args.indexDim(), args.normEps()) : null;
        this.indexKNorm = sparse ? new QwenRMSNorm(args.indexDim(), args.normEps()) : null;

        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(MemorySegment.NULL));
            register(arena, "q_proj", qProj);
            register(arena, "k_proj", kProj);
            register(arena, "v_proj", vProj);
            register(arena, "o_proj", oProj);
            smile_module_register_module(module, arena.allocateFrom("q_norm"), qNorm.module());
            smile_module_register_module(module, arena.allocateFrom("k_norm"), kNorm.module());
            if (sparse) {
                register(arena, "index_q_proj", indexQProj);
                register(arena, "index_k_proj", indexKProj);
                smile_module_register_module(module, arena.allocateFrom("index_q_norm"), indexQNorm.module());
                smile_module_register_module(module, arena.allocateFrom("index_k_norm"), indexKNorm.module());
            }
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Replaces the separate Marlin q/k/v (and index) projections by one GEMM over the concatenated output.
     * A no-op unless every projection is a loaded Marlin MXFP8 layer; call after the weights are loaded.
     */
    void fuseProjections() {
        if ("0".equals(System.getenv("SMILE_MINIMAX_FUSE_QKV")) || fusedProj != null) {
            return;
        }
        var layers = new java.util.ArrayList<LinearOp>(java.util.List.of(qProj, kProj, vProj));
        if (indexQProj != null) {
            layers.add(indexQProj);
            layers.add(indexKProj);
        }
        var parts = new smile.llm.quant.MxFp8MarlinLinear[layers.size()];
        for (int i = 0; i < parts.length; i++) {
            if (!(layers.get(i) instanceof smile.llm.quant.MxFp8MarlinLinear m) || !m.isLoaded()) {
                return;
            }
            parts[i] = m;
        }
        fusedProj = smile.llm.quant.MxFp8MarlinLinear.fuse(parts);
    }

    /** Output columns [from, to) of a fused projection as a view. */
    private static Tensor columns(Tensor proj, long from, long to) {
        return proj.get(smile.deep.tensor.Index.Ellipsis, smile.deep.tensor.Index.slice((int) from, (int) to));
    }

    /** Registers dense layers as submodules; FP8-resident projections own no parameters. */
    private void register(Arena arena, String name, LinearOp layer) {
        if (layer instanceof LinearLayer dense) {
            smile_module_register_module(module, arena.allocateFrom(name), dense.module());
        }
    }

    /**
     * Adds this layer's FP8-resident projections to {@code out}, keyed by their SMILE weight names.
     *
     * @param prefix name prefix, e.g. {@code layers.3.self_attn.}.
     * @param out    destination map.
     */
    void collect(String prefix, java.util.Map<String, MxFp8Layer> out) {
        collectOne(out, prefix + "q_proj.weight", qProj);
        collectOne(out, prefix + "k_proj.weight", kProj);
        collectOne(out, prefix + "v_proj.weight", vProj);
        collectOne(out, prefix + "o_proj.weight", oProj);
        collectOne(out, prefix + "index_q_proj.weight", indexQProj);
        collectOne(out, prefix + "index_k_proj.weight", indexKProj);
    }

    private static void collectOne(java.util.Map<String, MxFp8Layer> out, String name, LinearOp op) {
        if (op instanceof MxFp8Layer m) {
            out.put(name, m);
        }
    }

    @Override
    public MemorySegment module() {
        return module;
    }

    /**
     * Installs the KV pool.
     * @param pool KV pool (must not be {@code null}).
     */
    void setCachePool(KvCachePool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null");
        }
        this.cachePool = pool;
    }

    /**
     * Installs the MSA index-key cache.
     *
     * @param cache   index cache shared by all sparse layers.
     * @param ordinal this layer's ordinal among the sparse layers.
     */
    void setIndexCache(MiniMaxIndexCache cache, int ordinal) {
        this.indexCache = cache;
        this.indexOrdinal = ordinal;
    }

    private boolean useSparse() {
        return args != null && indexCache != null && !forceDense;
    }

    @Override
    public Tensor forward(Tensor x, int startPos, Tensor cis, Tensor mask) {
        throw new UnsupportedOperationException(
                "MiniMax attention requires HF cos/sin RoPE; use forward(x, positions, cos, sin, mask)");
    }

    /**
     * Forward pass.
     *
     * @param x         hidden states {@code [B, S, D]}.
     * @param positions KV write position per row; for {@code S > 1} all rows must share one position.
     * @param cos       RoPE cosines {@code [S, R]} (prefill) or per-row {@code [B, 1, R]} (decode).
     * @param sin       RoPE sines with the same layout.
     * @param mask      causal mask {@code [S, startPos + S]} for {@code S > 1}; ignored for decode.
     * @return attention output {@code [B, S, D]}.
     */
    public Tensor forward(Tensor x, int[] positions, Tensor cos, Tensor sin, Tensor mask) {
        if (cachePool == null) {
            throw new IllegalStateException("KV cache pool not installed; call setCachePool first");
        }
        long[] shape = x.shape();
        int batch = (int) shape[0];
        int seqlen = (int) shape[1];
        if (positions == null || positions.length != batch) {
            throw new IllegalArgumentException("positions length must equal batch size");
        }
        int maxPos = 0;
        for (int p : positions) {
            maxPos = Math.max(maxPos, p);
            if (seqlen > 1 && p != positions[0]) {
                throw new UnsupportedOperationException("multi-token windows require uniform positions");
            }
        }

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            long qw = (long) numHeads * headDim;
            long kw = (long) numKvHeads * headDim;
            Tensor proj = fusedProj != null ? fusedProj.forward(x) : null;
            Tensor qRaw = proj != null ? columns(proj, 0, qw) : qProj.forward(x);
            Tensor kRaw = proj != null ? columns(proj, qw, qw + kw) : kProj.forward(x);
            Tensor value = (proj != null ? columns(proj, qw + kw, qw + 2 * kw) : vProj.forward(x))
                    .reshape(batch, seqlen, numKvHeads, headDim);

            Tensor qRope;
            Tensor kRope;
            Tensor[] fused = Native.qkNormRope(qRaw.reshape(batch, seqlen, numHeads, headDim),
                    kRaw.reshape(batch, seqlen, numKvHeads, headDim),
                    qNorm.weight(), kNorm.weight(), cos, sin, rotaryDim, qNorm.eps());
            if (fused != null) {
                qRope = fused[0];
                kRope = fused[1];
            } else {
                Tensor query = headNorm(qRaw, qNorm, batch, seqlen, numHeads);
                Tensor key = headNorm(kRaw, kNorm, batch, seqlen, numKvHeads);
                var rope = PartialRotaryEncoding.apply(query, key, cos, sin, rotaryDim);
                qRope = rope._1();
                kRope = rope._2();
            }

            MiniMaxStep step = MiniMaxStep.current(positions);
            cachePool.put(kvLayerId, positions, kRope, value, step == null ? null : step.writeSlots(cachePool, seqlen));

            int cacheLen = step != null && step.fixedLength() > 0 ? step.fixedLength() : maxPos + seqlen;
            var cached = step == null ? cachePool.getPadded(kvLayerId, cacheLen)
                    : cachePool.getPadded(kvLayerId, cacheLen, step.readSlots(cachePool, cacheLen));
            // Decode: the numRep query heads of a KV group are the "query rows" of one attention over that
            // group's keys, so K/V are never expanded to all heads and the mask stays per group.
            boolean grouped = seqlen == 1 && numRep > 1;
            Tensor attnMask;
            if (useSparse()) {
                attnMask = sparseMask(x, cos, sin, positions, seqlen, cacheLen, step, grouped ? 1 : numRep, proj);
            } else {
                attnMask = seqlen > 1 ? mask : step != null
                        ? step.decodeMask(x.device(), x.dtype(), cacheLen) : decodeMask(positions, cacheLen, x);
            }
            double scale = 1.0 / Math.sqrt(headDim);
            Tensor merged;
            if (grouped) {
                Tensor attn = KERNEL.forward(qRope.reshape(batch, numKvHeads, numRep, headDim),
                        cached._1().transpose(1, 2), cached._2().transpose(1, 2),
                        attnMask, AttentionContext.contiguous(scale, 0.0, false));
                merged = attn.reshape(batch, 1, -1);
            } else {
                Tensor keys = repeatKV(cached._1(), numRep);
                Tensor values = repeatKV(cached._2(), numRep);
                Tensor attn = KERNEL.forward(qRope.transpose(1, 2), keys.transpose(1, 2), values.transpose(1, 2),
                        attnMask, AttentionContext.contiguous(scale, 0.0, false));
                merged = attn.transpose(1, 2).contiguous().view(batch, seqlen, -1);
            }
            Tensor out = oProj.forward(merged);
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }

    /**
     * MSA index branch: projects index queries/keys, stores the new keys, scores blocks over the
     * cached history and returns the additive mask restricting each head to its group's top-k blocks.
     */
    private Tensor sparseMask(Tensor x, Tensor cos, Tensor sin, int[] positions, int seqlen, int cacheLen,
                              MiniMaxStep step, int maskReps, Tensor proj) {
        int batch = positions.length;
        int groups = args.numIndexHeads();
        int idxDim = args.indexDim();
        int keyHeads = args.numIndexKeyHeads();
        long base = (long) (numHeads + 2 * numKvHeads) * headDim;
        long iqWidth = (long) groups * idxDim;
        Tensor iqRaw = proj != null ? columns(proj, base, base + iqWidth) : indexQProj.forward(x);
        Tensor ikRaw = proj != null ? columns(proj, base + iqWidth, base + iqWidth + (long) keyHeads * idxDim)
                : indexKProj.forward(x);
        Tensor[] fused = Native.qkNormRope(iqRaw.reshape(batch, seqlen, groups, idxDim),
                ikRaw.reshape(batch, seqlen, keyHeads, idxDim),
                indexQNorm.weight(), indexKNorm.weight(), cos, sin, rotaryDim, indexQNorm.eps());
        Tensor ropeQ;
        Tensor ropeK;
        if (fused != null) {
            ropeQ = fused[0];
            ropeK = fused[1];
        } else {
            Tensor iq = indexNorm(iqRaw, indexQNorm, batch, seqlen, groups, idxDim);
            Tensor ik = indexNorm(ikRaw, indexKNorm, batch, seqlen, keyHeads, idxDim);
            var rope = PartialRotaryEncoding.apply(iq, ik, cos, sin, rotaryDim);
            ropeQ = rope._1();
            ropeK = rope._2();
        }

        Tensor writeIdx = step == null ? null : step.writeSlots(cachePool, seqlen);
        if (writeIdx != null) {
            indexCache.put(indexOrdinal, writeIdx, ropeK);
        } else {
            indexCache.put(indexOrdinal, cachePool.slotIndices(positions, seqlen), ropeK);
        }
        if (seqlen == 1 && step != null && indexCache.numKeyHeads() == 1) {
            try (Tensor layer = indexCache.layer(indexOrdinal)) {
                Tensor mask = Native.msaDecodeMask(ropeQ.reshape(batch, groups, idxDim), layer,
                        step.readSlots(cachePool, cacheLen), step.positionsDevice(x.device()), cacheLen,
                        args.blockSize(), args.topkBlocks(), args.initBlocks(), args.localBlocks());
                if (mask != null) {
                    return mask;
                }
            }
        }
        Tensor history = (step == null ? indexCache.get(indexOrdinal, cachePool.slotIndices(cacheLen))
                : indexCache.get(indexOrdinal, step.readSlots(cachePool, cacheLen)))
                .reshape(batch, cacheLen, args.numIndexKeyHeads(), idxDim);
        Tensor allow = MiniMaxSparse.allowMask(ropeQ, history, positions, args.blockSize(),
                args.topkBlocks(), args.initBlocks(), args.localBlocks());
        return MiniMaxSparse.additiveMask(allow, maskReps, x.dtype());
    }

    private Tensor indexNorm(Tensor proj, QwenRMSNorm norm, int batch, int seqlen, int heads, int dim) {
        Tensor flat = proj.reshape((long) batch * seqlen * heads, dim);
        return norm.forward(flat).view(batch, seqlen, heads, dim);
    }

    /** Projects to heads, applies per-head QK RMSNorm, returns {@code [B, S, H, D]}. */
    private Tensor headNorm(Tensor proj, QwenRMSNorm norm, int batch, int seqlen, int heads) {
        Tensor flat = proj.reshape((long) batch * seqlen * heads, headDim);
        return norm.forward(flat).view(batch, seqlen, heads, headDim);
    }

    /** Additive mask {@code [B, 1, 1, cacheLen]}: row b may attend to positions {@code <= positions[b]}. */
    private Tensor decodeMask(int[] positions, int cacheLen, Tensor like) {
        float[] data = new float[positions.length * cacheLen];
        for (int b = 0; b < positions.length; b++) {
            for (int j = positions[b] + 1; j < cacheLen; j++) {
                data[b * cacheLen + j] = Float.NEGATIVE_INFINITY;
            }
        }
        return Tensor.of(data, positions.length, 1, 1, cacheLen).to(like.device()).to(like.dtype());
    }
}
