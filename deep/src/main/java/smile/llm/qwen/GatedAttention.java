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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.activation.Sigmoid;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCacheLayout;
import smile.llm.cache.KvCachePool;
import smile.llm.parallel.TensorParallelGroup;
import smile.llm.parallel.TensorShardSpec;
import smile.llm.transformer.Attention;
import smile.torch.Native;
import smile.util.AutoScope;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_module;

/**
 * Qwen3.5 gated full attention (GQA + partial RoPE + sigmoid output gate).
 *
 * <p>The query projection emits twice the head channels; the second half is a
 * per-token sigmoid gate applied to the attention output before {@code o_proj}.
 *
 * <p>Under tensor parallelism, Q/K/V projections are column-sharded by heads and
 * {@code o_proj} is row-parallel (all-reduce after the projection).
 *
 * @author Haifeng Li
 */
public class GatedAttention implements Attention {
    final MemorySegment module;
    final int numHeads;
    final int numKvHeads;
    final int numRep;
    final int headDim;
    final int rotaryDim;
    final LinearLayer qProj;
    final LinearLayer kProj;
    final LinearLayer vProj;
    final LinearLayer oProj;
    final QwenRMSNorm qNorm;
    final QwenRMSNorm kNorm;
    final Sigmoid sigmoid = new Sigmoid(false);
    KvCachePool cachePool;
    /** Index within the shared KV pool (full-attention ordinal). */
    final int kvLayerId;
    final TensorParallelGroup tpGroup;
    final int tpRank;

    /**
     * Constructor.
     *
     * @param dim        hidden size.
     * @param numHeads   query head count (local under TP).
     * @param numKvHeads key/value head count (local under TP).
     * @param headDim    per-head dimension.
     * @param rotaryDim  partial RoPE dimension.
     * @param normEps    RMSNorm epsilon.
     * @param cachePool  shared KV cache pool.
     * @param kvLayerId  layer index inside the KV pool.
     */
    public GatedAttention(int dim, int numHeads, int numKvHeads, int headDim, int rotaryDim,
                          double normEps, KvCachePool cachePool, int kvLayerId) {
        this(dim, numHeads, numKvHeads, headDim, rotaryDim, normEps, cachePool, kvLayerId, null, 0);
    }

    /**
     * Tensor-parallel constructor.
     */
    public GatedAttention(int dim, int numHeads, int numKvHeads, int headDim, int rotaryDim,
                          double normEps, KvCachePool cachePool, int kvLayerId,
                          TensorParallelGroup tpGroup, int tpRank) {
        if (cachePool == null) throw new IllegalArgumentException("cachePool must not be null");
        if (numHeads % numKvHeads != 0) {
            throw new IllegalArgumentException("numHeads must be divisible by numKvHeads");
        }
        this.numHeads = numHeads;
        this.numKvHeads = numKvHeads;
        this.numRep = numHeads / numKvHeads;
        this.headDim = headDim;
        this.rotaryDim = rotaryDim;
        this.cachePool = cachePool;
        this.kvLayerId = kvLayerId;
        this.tpGroup = tpGroup;
        this.tpRank = tpRank;

        this.qProj = new LinearLayer(dim, numHeads * headDim * 2, false);
        this.kProj = new LinearLayer(dim, numKvHeads * headDim, false);
        this.vProj = new LinearLayer(dim, numKvHeads * headDim, false);
        this.oProj = new LinearLayer(numHeads * headDim, dim, false);
        this.qNorm = new QwenRMSNorm(headDim, normEps);
        this.kNorm = new QwenRMSNorm(headDim, normEps);

        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(MemorySegment.NULL));
            smile_module_register_module(module, arena.allocateFrom("q_proj"), qProj.module());
            smile_module_register_module(module, arena.allocateFrom("k_proj"), kProj.module());
            smile_module_register_module(module, arena.allocateFrom("v_proj"), vProj.module());
            smile_module_register_module(module, arena.allocateFrom("o_proj"), oProj.module());
            smile_module_register_module(module, arena.allocateFrom("q_norm"), qNorm.module());
            smile_module_register_module(module, arena.allocateFrom("k_norm"), kNorm.module());
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Builds attention from a shard spec (local head counts).
     */
    public static GatedAttention forShard(int dim, int headDim, int rotaryDim, double normEps,
                                          KvCachePool cachePool, int kvLayerId,
                                          TensorShardSpec shard, TensorParallelGroup tpGroup) {
        return new GatedAttention(dim, shard.numHeads(), shard.numKvHeads(), headDim, rotaryDim,
                normEps, cachePool, kvLayerId, tpGroup, shard.tpRank());
    }

    /**
     * Test helper that allocates a private KV pool.
     */
    public GatedAttention(int dim, int numHeads, int numKvHeads, int headDim, int rotaryDim,
                          double normEps, KvCacheLayout layout) {
        this(dim, numHeads, numKvHeads, headDim, rotaryDim, normEps,
                KvCachePool.forTesting(layout, Device.CPU()), 0);
    }

    @Override
    public MemorySegment module() {
        return module;
    }

    void setCachePool(KvCachePool pool) {
        if (pool == null) throw new IllegalArgumentException("pool must not be null");
        this.cachePool = pool;
    }

    @Override
    public Tensor forward(Tensor x, int startPos, Tensor cis, Tensor mask) {
        long[] shape = x.shape();
        int batchSize = (int) shape[0];
        int seqlen = (int) shape[1];

        try (var scope = new AutoScope()) {
            Tensor qFull = scope.add(qProj.forward(x).view(batchSize, seqlen, numHeads, headDim * 2));
            Tensor query;
            Tensor gate;
            try (var qSlice = smile.deep.tensor.Index.slice(0, headDim);
                 var gSlice = smile.deep.tensor.Index.slice(headDim, headDim * 2)) {
                query = scope.add(qFull.get(smile.deep.tensor.Index.Ellipsis, qSlice));
                gate = scope.add(qFull.get(smile.deep.tensor.Index.Ellipsis, gSlice)
                        .reshape(batchSize, seqlen, numHeads * headDim));
            }

            Tensor key = scope.add(kProj.forward(x).view(batchSize, seqlen, numKvHeads, headDim));
            Tensor value = scope.add(vProj.forward(x).view(batchSize, seqlen, numKvHeads, headDim));

            query = scope.add(qNorm.forward(query.reshape(batchSize * seqlen * numHeads, headDim))
                    .view(batchSize, seqlen, numHeads, headDim));
            key = scope.add(kNorm.forward(key.reshape(batchSize * seqlen * numKvHeads, headDim))
                    .view(batchSize, seqlen, numKvHeads, headDim));

            var rope = PartialRotaryEncoding.apply(query, key, cis, rotaryDim);
            query = scope.add(rope._1());
            key = scope.add(rope._2());

            cachePool.put(kvLayerId, startPos, key, value);
            var cached = cachePool.get(kvLayerId, startPos + seqlen);
            Tensor keys = scope.add(cached._1());
            Tensor values = scope.add(cached._2());

            keys = scope.add(repeatKV(keys, numRep));
            values = scope.add(repeatKV(values, numRep));

            query = scope.add(query.transpose(1, 2));
            keys = scope.add(keys.transpose(1, 2));
            values = scope.add(values.transpose(1, 2));

            double scale = 1.0 / Math.sqrt(headDim);
            Tensor attn = scope.add(apply(query, keys, values, mask, 0.0, false, scale));
            attn = scope.add(attn.transpose(1, 2).contiguous().view(batchSize, seqlen, -1));
            attn = scope.add(attn.mul(sigmoid.forward(gate)));
            Tensor out = oProj.forward(attn);
            if (tpGroup != null && tpGroup.tpSize() > 1) {
                tpGroup.allReduceSumInPlace(tpRank, out);
            }
            return out;
        }
    }
}
