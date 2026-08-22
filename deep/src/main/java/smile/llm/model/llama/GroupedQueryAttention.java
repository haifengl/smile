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
package smile.llm.model.llama;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Device;
import smile.torch.Native;
import smile.deep.tensor.Tensor;
import smile.llm.attention.AttentionBackend;
import smile.llm.attention.AttentionBackends;
import smile.llm.attention.AttentionContext;
import smile.llm.cache.FlashInferKvMetadata;
import smile.llm.cache.KvCacheLayout;
import smile.llm.cache.KvCachePool;
import smile.llm.attention.Attention;
import smile.llm.transformer.RotaryPositionalEncoding;
import smile.util.AutoScope;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_module;

/**
 * Grouped Query Attention (GQA). GQA is a highly efficient transformer
 * attention mechanism that bridges the gap between traditional
 * Multi-Head Attention (MHA) and Multi-Query Attention (MQA).
 * By grouping sets of query heads to share a single Key and Value head,
 * GQA drastically reduces memory usage during inference while maintaining
 * model quality.
 *
 * <p>KV activations are stored in a shared {@link KvCachePool} managed by
 * the inference engine (e.g. smile-serve), enabling radix-tree prefix reuse
 * across requests instead of allocating a private
 * {@code maxBatchSize × maxSeqLen} buffer per layer.
 *
 * @author Haifeng Li
 */
public class GroupedQueryAttention implements Attention {
    /** PyTorch module. */
    final MemorySegment module;
    /** The number of key and value heads. */
    final int numKvHeads;
    /** The number of local query heads. */
    final int numLocalHeads;
    /** The number of local key and value heads. */
    final int numLocalKvHeads;
    /** The number of repetitions for local heads. */
    final int numRep;
    /** The embedding dimension of each attention head. */
    final int headDim;
    /** Linear transformation for queries, keys, values, and output. */
    final LinearLayer wq, wk, wv, wo;
    /** Shared KV cache pool owned by the inference engine; set after weight load. */
    KvCachePool cachePool;
    /** Index of this layer within the transformer stack. */
    final int layerId;

    /**
     * Constructor. Install a {@link KvCachePool} via {@link #setCachePool}
     * before the first {@link #forward}.
     *
     * @param dim        token embedding dimension.
     * @param numHeads   number of query heads.
     * @param numKvHeads number of key/value heads.
     * @param layerId    zero-based layer index within the transformer.
     */
    public GroupedQueryAttention(int dim, int numHeads, int numKvHeads, int layerId) {
        if (numHeads < 1) {
            throw new IllegalArgumentException("numHeads must be >= 1");
        }
        if (numKvHeads < 1) {
            throw new IllegalArgumentException("numKvHeads must be >= 1");
        }
        if (dim % numHeads != 0) {
            throw new IllegalArgumentException("dim must be divisible by numHeads");
        }
        this.cachePool = null;
        this.layerId = layerId;
        this.numKvHeads = numKvHeads;
        // Don't support torch.distributed yet
        int modelParallelSize = 1; // torch.distributed.get_world_size(group=get_model_parallel_group());
        this.numLocalHeads = numHeads / modelParallelSize;
        this.numLocalKvHeads = this.numKvHeads / modelParallelSize;
        this.numRep = this.numLocalHeads / this.numLocalKvHeads;
        this.headDim = dim / numHeads;

        this.wq = new LinearLayer(dim, numHeads * headDim, false);
        this.wk = new LinearLayer(dim, numKvHeads * headDim, false);
        this.wv = new LinearLayer(dim, numKvHeads * headDim, false);
        this.wo = new LinearLayer(numHeads * headDim, dim, false);

        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(MemorySegment.NULL));
            smile_module_register_module(module, arena.allocateFrom("wq"), wq.module());
            smile_module_register_module(module, arena.allocateFrom("wk"), wk.module());
            smile_module_register_module(module, arena.allocateFrom("wv"), wv.module());
            smile_module_register_module(module, arena.allocateFrom("wo"), wo.module());
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Convenience constructor that allocates a small test pool sized from
     * {@code layout}. Prefer installing a shared pool in production.
     *
     * @param dim        token embedding dimension.
     * @param numHeads   number of query heads.
     * @param numKvHeads number of key/value heads.
     * @param layout     cache layout used for the private test pool.
     */
    public GroupedQueryAttention(int dim, int numHeads, int numKvHeads, KvCacheLayout layout) {
        this(dim, numHeads, numKvHeads, 0);
        setCachePool(KvCachePool.forTesting(layout, Device.CPU()));
    }

    @Override
    public MemorySegment module() {
        return module;
    }

    /**
     * Installs the KV cache pool (after weights are loaded so the pool can be
     * sized from residual GPU memory).
     *
     * @param cachePool the shared pool (must not be {@code null}).
     */
    void setCachePool(KvCachePool cachePool) {
        if (cachePool == null) throw new IllegalArgumentException("cachePool must not be null");
        this.cachePool = cachePool;
    }

    @Override
    public Tensor forward(Tensor x, int startPos, Tensor cis, Tensor mask) {
        if (cachePool == null) {
            throw new IllegalStateException("KV cache pool not installed; call setCachePool first");
        }
        long[] shape = x.shape();
        int batchSize = (int) shape[0];
        int seqlen = (int) shape[1];

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor qRaw = wq.forward(x);
            Tensor xq = qRaw.view(batchSize, seqlen, numLocalHeads, headDim);
            Tensor kRaw = wk.forward(x);
            Tensor xk = kRaw.view(batchSize, seqlen, numLocalKvHeads, headDim);
            Tensor vRaw = wv.forward(x);
            Tensor xv = vRaw.view(batchSize, seqlen, numLocalKvHeads, headDim);

            var tuple = RotaryPositionalEncoding.apply(xq, xk, cis);
            Tensor qRope = tuple._1();
            Tensor kRope = tuple._2();

            // put copies into the pool; RoPE keys are not needed afterward.
            cachePool.put(layerId, startPos, kRope, xv);
            kRope.close();

            int cacheLen = startPos + seqlen;
            Tensor qT = qRope.transpose(1, 2);
            Tensor attn;
            if (AttentionBackends.current() == AttentionBackend.FLASHINFER) {
                try (FlashInferKvMetadata meta = cachePool.buildFlashInferMetadata(cacheLen)) {
                    // Match torch_native: causality comes from {@code mask}, not is_causal.
                    var ctx = AttentionContext.paged(
                            0.0, false,
                            numLocalHeads, numLocalKvHeads, headDim,
                            layerId, startPos, seqlen, cacheLen,
                            cachePool, meta, cachePool.flashInferWorkspace());
                    attn = AttentionBackends.kernel().forward(qT, null, null, mask, ctx);
                }
            } else {
                var cached = cachePool.get(layerId, cacheLen);
                Tensor keys = cached._1();
                Tensor values = cached._2();

                Tensor keysRep = repeatKV(keys, numRep);
                Tensor valuesRep = repeatKV(values, numRep);
                if (keysRep != keys) {
                    keys.close();
                }
                if (valuesRep != values) {
                    values.close();
                }

                Tensor kT = keysRep.transpose(1, 2);
                Tensor vT = valuesRep.transpose(1, 2);
                attn = apply(qT, kT, vT, mask);
            }
            Tensor attnT = attn.transpose(1, 2);
            Tensor attnC = attnT.contiguous();
            Tensor flat = attnC.view(batchSize, seqlen, -1);
            Tensor out = wo.forward(flat);
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }
}
