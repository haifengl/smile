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
package smile.llm.transformer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Index;
import smile.torch.Native;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
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
    /** Cached keys and values. */
    final Tensor cacheK, cacheV;

    /**
     * Constructor.
     * @param args the model configuration parameters.
     */
    public GroupedQueryAttention(ModelArgs args) {
        this.numKvHeads = args.numKvHeads() == null ? args.numHeads() : args.numKvHeads();
        // Don't support torch.distributed yet
        int modelParallelSize = 1; // torch.distributed.get_world_size(group=get_model_parallel_group());
        this.numLocalHeads = args.numHeads() / modelParallelSize;
        this.numLocalKvHeads = this.numKvHeads / modelParallelSize;
        this.numRep = this.numLocalHeads / this.numLocalKvHeads;
        this.headDim = args.dim() / args.numHeads();

        this.wq = new LinearLayer(args.dim(), args.numHeads() * headDim, false);
        this.wk = new LinearLayer(args.dim(), numKvHeads * headDim, false);
        this.wv = new LinearLayer(args.dim(), numKvHeads * headDim, false);
        this.wo = new LinearLayer(args.numHeads() * headDim, args.dim(), false);

        this.cacheK = Tensor.zeros(args.maxBatchSize(), args.maxSeqLen(), numLocalKvHeads, headDim);
        this.cacheV = Tensor.zeros(args.maxBatchSize(), args.maxSeqLen(), numLocalKvHeads, headDim);

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

    @Override
    public MemorySegment module() {
        return module;
    }

    @Override
    public Tensor forward(Tensor x, int startPos, Tensor cis, Tensor mask) {
        long[] shape = x.shape();
        int batchSize = (int) shape[0];
        int seqlen = (int) shape[1];

        try (var scope = new AutoScope()) {
            Tensor xq = scope.add(wq.forward(x).view(batchSize, seqlen, numLocalHeads, headDim));
            Tensor xk = scope.add(wk.forward(x).view(batchSize, seqlen, numLocalKvHeads, headDim));
            Tensor xv = scope.add(wv.forward(x).view(batchSize, seqlen, numLocalKvHeads, headDim));

            var tuple = RotaryPositionalEncoding.apply(xq, xk, cis);
            xq = scope.add(tuple._1());
            xk = scope.add(tuple._2());

            try (var batch = Index.slice(0, batchSize);
                 var span = Index.slice(startPos, startPos + seqlen)) {
                cacheK.put_(xk, batch, span);
                cacheV.put_(xv, batch, span);
            }

            Tensor keys;
            Tensor values;
            try (var batch = Index.slice(0, batchSize);
                 var span = Index.slice(0, startPos + seqlen)) {
                keys = scope.add(cacheK.get(batch, span));
                values = scope.add(cacheV.get(batch, span));
            }

            // repeat k/v heads if n_kv_heads < n_heads
            keys = scope.add(repeatKV(keys, numRep));
            values = scope.add(repeatKV(values, numRep));

            xq = scope.add(xq.transpose(1, 2));  // (bs, n_local_heads, seqlen, head_dim)
            keys = scope.add(keys.transpose(1, 2));  // (bs, n_local_heads, cache_len + seqlen, head_dim)
            values = scope.add(values.transpose(1, 2));  // (bs, n_local_heads, cache_len + seqlen, head_dim)
            Tensor keysT = scope.add(keys.transpose(2, 3));
            Tensor scores = scope.add(xq.matmul(keysT).div_(Math.sqrt(headDim)));
            if (mask != null) {
                scores = scope.add(scores.add(mask));  // (bs, n_local_heads, seqlen, cache_len + seqlen)
            }
            scores = scope.add(scores.to(ScalarType.Float).softmax(-1).to(xq.dtype()));
            Tensor output = scope.add(scores.matmul(values));  // (bs, n_local_heads, seqlen, head_dim)
            output = scope.add(output.transpose(1, 2).contiguous().view(batchSize, seqlen, -1));
            return wo.forward(output);
        }
    }
}
