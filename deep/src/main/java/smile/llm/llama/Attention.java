/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.llama;

import org.bytedeco.pytorch.Module;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.RotaryPositionalEncoding;

/**
 * Multi-head attention. It caches key and value information, applying rotary
 * embeddings, and performing linear transformations.
 *
 * @author Haifeng Li
 */
public class Attention {
    /** PyTorch module. */
    final Module module;
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
    public Attention(ModelArgs args) {
        this.numKvHeads = args.numKvHeads() == null ? args.numHeads() : args.numKvHeads();
        // JavaCPP doesn't support torch.distributed yet
        int modelParallelSize = 1; //fs_init.get_model_parallel_world_size();
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

        this.module = new Module();
        this.module.register_module("wq", wq.asTorch());
        this.module.register_module("wk", wk.asTorch());
        this.module.register_module("wv", wv.asTorch());
        this.module.register_module("wo", wo.asTorch());
    }

    /**
     * Forward pass through the attention module.
     * @param x the input tensor.
     * @param startPos the starting position for attention caching.
     * @param cis the precomputed frequency tensor.
     * @param mask the attention mask tensor.
     * @return the output tensor.
     */
    public Tensor forward(Tensor x, int startPos, Tensor cis, Tensor mask) {
        long[] shape = x.shape();
        int batchSize = (int) shape[0];
        int seqlen = (int) shape[1];

        Tensor xq = wq.forward(x);
        Tensor xk = wk.forward(x);
        Tensor xv = wv.forward(x);

        xq = xq.view(batchSize, seqlen, numLocalHeads, headDim);
        xk = xk.view(batchSize, seqlen, numLocalKvHeads, headDim);
        xv = xv.view(batchSize, seqlen, numLocalKvHeads, headDim);

        var tuple = RotaryPositionalEncoding.apply(xq, xk, cis);
        xq = tuple._1();
        xk = tuple._2();

        cacheK.put_(xk, Index.slice(0, batchSize), Index.slice(startPos, startPos + seqlen));
        cacheV.put_(xv, Index.slice(0, batchSize), Index.slice(startPos, startPos + seqlen));

        var keys = cacheK.get(Index.slice(0, batchSize), Index.slice(0, startPos + seqlen));
        var values = cacheV.get(Index.slice(0, batchSize), Index.slice(0, startPos + seqlen));

        // repeat k/v heads if n_kv_heads < n_heads
        keys = repeatKV(keys, numRep);  // (bs, cache_len + seqlen, n_local_heads, head_dim)
        values = repeatKV(values, numRep);  // (bs, cache_len + seqlen, n_local_heads, head_dim)

        xq = xq.transpose(1, 2);  // (bs, n_local_heads, seqlen, head_dim)
        keys = keys.transpose(1, 2);  // (bs, n_local_heads, cache_len + seqlen, head_dim)
        values = values.transpose(1, 2);  // (bs, n_local_heads, cache_len + seqlen, head_dim)
        var scores = xq.matmul(keys.transpose(2, 3)).div_(Math.sqrt(headDim));
        if (mask != null) {
            scores = scores.add_(mask);  // (bs, n_local_heads, seqlen, cache_len + seqlen)
        }
        scores = scores.to(ScalarType.Float32).softmax(-1).to(xq.dtype());
        var output = scores.matmul(values);  // (bs, n_local_heads, seqlen, head_dim)
        output = output.transpose(1, 2).contiguous().view(batchSize, seqlen, -1);
        return wo.forward(output);
    }

    /**
     * Efficiently repeat a tensor.
     * @param input the input tensor to repeat.
     * @param numRep the number of times to repeat.
     * @return the repeated tensor.
     */
    private Tensor repeatKV(Tensor input, int numRep) {
        if (numRep == 1) {
            return input;
        } else {
            long[] shape = input.shape();
            long batchSize = shape[0];
            long seqlen = shape[1];
            long numKvHeads = shape[2];
            long headDim = shape[3];
            try (var x = input.get(Index.Colon, Index.Colon, Index.Colon, Index.None, Index.Colon)) {
                return x.expand(batchSize, seqlen, numKvHeads, numRep, headDim)
                        .reshape(batchSize, seqlen, numKvHeads * numRep, headDim);
            }
        }
    }
}
