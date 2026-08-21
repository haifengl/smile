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
     * Constructor. Install a {@link KvCachePool} via {@link #setCachePool}
     * before the first {@link #forward}.
     *
     * @param dim        hidden size.
     * @param numHeads   query head count (local under TP).
     * @param numKvHeads key/value head count (local under TP).
     * @param headDim    per-head dimension.
     * @param rotaryDim  partial RoPE dimension.
     * @param normEps    RMSNorm epsilon.
     * @param kvLayerId  layer index inside the KV pool.
     */
    public GatedAttention(int dim, int numHeads, int numKvHeads, int headDim, int rotaryDim,
                          double normEps, int kvLayerId) {
        this(dim, numHeads, numKvHeads, headDim, rotaryDim, normEps, kvLayerId, null, 0);
    }

    /**
     * Tensor-parallel constructor.
     *
     * @param dim        hidden size.
     * @param numHeads   query head count (local under TP).
     * @param numKvHeads key/value head count (local under TP).
     * @param headDim    per-head dimension.
     * @param rotaryDim  partial RoPE dimension.
     * @param normEps    RMSNorm epsilon.
     * @param kvLayerId  layer index inside the KV pool.
     * @param tpGroup    tensor-parallel group, or {@code null} for single-device.
     * @param tpRank     this rank's TP index.
     */
    public GatedAttention(int dim, int numHeads, int numKvHeads, int headDim, int rotaryDim,
                          double normEps, int kvLayerId,
                          TensorParallelGroup tpGroup, int tpRank) {
        if (numHeads % numKvHeads != 0) {
            throw new IllegalArgumentException("numHeads must be divisible by numKvHeads");
        }
        this.numHeads = numHeads;
        this.numKvHeads = numKvHeads;
        this.numRep = numHeads / numKvHeads;
        this.headDim = headDim;
        this.rotaryDim = rotaryDim;
        this.cachePool = null;
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
     *
     * @param dim        hidden size.
     * @param headDim    per-head dimension.
     * @param rotaryDim  partial RoPE dimension.
     * @param normEps    RMSNorm epsilon.
     * @param kvLayerId  layer index inside the KV pool.
     * @param shard      local head / rank shard description.
     * @param tpGroup    tensor-parallel group.
     * @return gated attention for the shard.
     */
    public static GatedAttention forShard(int dim, int headDim, int rotaryDim, double normEps,
                                          int kvLayerId,
                                          TensorShardSpec shard, TensorParallelGroup tpGroup) {
        return new GatedAttention(dim, shard.numHeads(), shard.numKvHeads(), headDim, rotaryDim,
                normEps, kvLayerId, tpGroup, shard.tpRank());
    }

    /**
     * Test helper that allocates a private KV pool.
     *
     * @param dim        hidden size.
     * @param numHeads   query head count.
     * @param numKvHeads key/value head count.
     * @param headDim    per-head dimension.
     * @param rotaryDim  partial RoPE dimension.
     * @param normEps    RMSNorm epsilon.
     * @param layout     KV cache layout used to size the private pool.
     */
    public GatedAttention(int dim, int numHeads, int numKvHeads, int headDim, int rotaryDim,
                          double normEps, KvCacheLayout layout) {
        this(dim, numHeads, numKvHeads, headDim, rotaryDim, normEps, 0);
        setCachePool(KvCachePool.forTesting(layout, Device.CPU()));
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
        throw new UnsupportedOperationException(
                "Qwen gated attention requires HF cos/sin RoPE; use forward(x, startPos, cos, sin, mask)");
    }

    /**
     * Forward with HuggingFace-style partial RoPE cos/sin tables.
     *
     * @param x        hidden states {@code [B, S, D]}.
     * @param startPos KV cache write position.
     * @param cos      cosines for this window {@code [S, rotaryDim]}.
     * @param sin      sines for this window {@code [S, rotaryDim]}.
     * @param mask     causal attention mask, or {@code null} when {@code S == 1}.
     * @return attention output {@code [B, S, D]}.
     */
    public Tensor forward(Tensor x, int startPos, Tensor cos, Tensor sin, Tensor mask) {
        if (cachePool == null) {
            throw new IllegalStateException("KV cache pool not installed; call setCachePool first");
        }
        long[] shape = x.shape();
        int batchSize = (int) shape[0];
        int seqlen = (int) shape[1];

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor qRaw = qProj.forward(x);
            Tensor qFull = qRaw.view(batchSize, seqlen, numHeads, headDim * 2);
            Tensor query;
            Tensor gate;
            try (var qSlice = smile.deep.tensor.Index.slice(0, headDim);
                 var gSlice = smile.deep.tensor.Index.slice(headDim, headDim * 2)) {
                query = qFull.get(smile.deep.tensor.Index.Ellipsis, qSlice);
                Tensor gateSlice = qFull.get(smile.deep.tensor.Index.Ellipsis, gSlice);
                gate = gateSlice.reshape(batchSize, seqlen, numHeads * headDim);
            }

            Tensor kRaw = kProj.forward(x);
            Tensor key = kRaw.view(batchSize, seqlen, numKvHeads, headDim);
            Tensor vRaw = vProj.forward(x);
            Tensor value = vRaw.view(batchSize, seqlen, numKvHeads, headDim);

            Tensor qFlat = query.reshape(batchSize * seqlen * numHeads, headDim);
            Tensor qNormed = qNorm.forward(qFlat);
            query = qNormed.view(batchSize, seqlen, numHeads, headDim);

            Tensor kFlat = key.reshape(batchSize * seqlen * numKvHeads, headDim);
            Tensor kNormed = kNorm.forward(kFlat);
            key = kNormed.view(batchSize, seqlen, numKvHeads, headDim);

            var rope = PartialRotaryEncoding.apply(query, key, cos, sin, rotaryDim);
            Tensor qRope = rope._1();
            Tensor kRope = rope._2();

            cachePool.put(kvLayerId, startPos, kRope, value);
            kRope.close();

            var cached = cachePool.get(kvLayerId, startPos + seqlen);
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

            Tensor qT = qRope.transpose(1, 2);
            Tensor kT = keysRep.transpose(1, 2);
            Tensor vT = valuesRep.transpose(1, 2);

            double scale = 1.0 / Math.sqrt(headDim);
            Tensor attn = apply(qT, kT, vT, mask, 0.0, false, scale);
            Tensor attnT = attn.transpose(1, 2);
            Tensor attnC = attnT.contiguous();
            attn = attnC.view(batchSize, seqlen, -1);
            Tensor gateSig = sigmoid.forward(gate);
            Tensor gated = attn.mul(gateSig);
            Tensor out = oProj.forward(gated);
            if (tpGroup != null && tpGroup.tpSize() > 1) {
                tpGroup.allReduceSumInPlace(tpRank, out);
            }
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }
}
