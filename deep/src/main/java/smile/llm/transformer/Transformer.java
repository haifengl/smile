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

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import smile.deep.layer.EmbeddingLayer;
import smile.deep.layer.LinearLayer;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.RMSNormLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCacheLayout;
import smile.llm.cache.KvCachePool;
import smile.util.AutoScope;

import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_list_create;
import static smile.torch.smile_torch_h.smile_module_list_free;
import static smile.torch.smile_torch_h.smile_module_list_push_back;
import static smile.torch.smile_torch_h.smile_module_list_as_module;

/**
 * The Transformer model. It consists of token embeddings, stacked
 * Transformer blocks, and the final output layer. This model can
 * be used for various natural language processing tasks, such as
 * language modeling or text generation.
 *
 * <p>Family-specific hyperparameters (Llama, Qwen, …) live outside this
 * class; pass the resolved dimensional primitives here.
 *
 * @author Haifeng Li
 */
public class Transformer extends LayerBlock {
    /** The vocabulary size. */
    final int vocabSize;
    /** The number of transformer blocks. */
    final int numLayers;
    /** Token embeddings. */
    final EmbeddingLayer tokEmbeddings;
    /** Transformer blocks. */
    final List<TransformerBlock> layers;
    /** The layer normalization for the model output. */
    final RMSNormLayer norm;
    /** The linear layer for final output. */
    final LinearLayer output;
    /** The precomputed cosine and sine frequencies. */
    final Tensor cis;
    /** Shared KV cache pool used by all attention layers. */
    KvCachePool kvCachePool;

    /**
     * Constructor that allocates a small test-sized KV cache pool.
     *
     * @param dim                token embedding dimension.
     * @param numLayers          number of transformer blocks.
     * @param numHeads           number of query heads.
     * @param numKvHeads         number of key/value heads.
     * @param vocabSize          vocabulary size.
     * @param intermediateSize   explicit FFN hidden size, or {@code null}.
     * @param multipleOf         FFN rounding multiple when deriving size.
     * @param ffnDimMultiplier   optional FFN dim multiplier.
     * @param normEps            RMSNorm epsilon.
     * @param ropeTheta          RoPE theta.
     * @param scaledRope         whether to use Llama-3 scaled RoPE.
     * @param maxSeqLen          maximum sequence length (RoPE table uses {@code 2 * maxSeqLen}).
     * @param layout             cache layout for the private test pool.
     * @param device             compute device.
     */
    public Transformer(int dim, int numLayers, int numHeads, int numKvHeads, int vocabSize,
                       Integer intermediateSize, int multipleOf, Double ffnDimMultiplier,
                       double normEps, double ropeTheta, boolean scaledRope, int maxSeqLen,
                       KvCacheLayout layout, Device device) {
        this(dim, numLayers, numHeads, numKvHeads, vocabSize, intermediateSize, multipleOf,
                ffnDimMultiplier, normEps, ropeTheta, scaledRope, maxSeqLen,
                KvCachePool.forTesting(layout, device), device);
    }

    /**
     * Constructor.
     *
     * @param dim                token embedding dimension.
     * @param numLayers          number of transformer blocks.
     * @param numHeads           number of query heads.
     * @param numKvHeads         number of key/value heads.
     * @param vocabSize          vocabulary size.
     * @param intermediateSize   explicit FFN hidden size, or {@code null}.
     * @param multipleOf         FFN rounding multiple when deriving size.
     * @param ffnDimMultiplier   optional FFN dim multiplier.
     * @param normEps            RMSNorm epsilon.
     * @param ropeTheta          RoPE theta.
     * @param scaledRope         whether to use Llama-3 scaled RoPE.
     * @param maxSeqLen          maximum sequence length (RoPE table uses {@code 2 * maxSeqLen}).
     * @param kvCachePool        shared KV cache pool.
     * @param device             compute device.
     */
    public Transformer(int dim, int numLayers, int numHeads, int numKvHeads, int vocabSize,
                       Integer intermediateSize, int multipleOf, Double ffnDimMultiplier,
                       double normEps, double ropeTheta, boolean scaledRope, int maxSeqLen,
                       KvCachePool kvCachePool, Device device) {
        if (kvCachePool == null) {
            throw new IllegalArgumentException("kvCachePool must not be null");
        }
        this.vocabSize = vocabSize;
        this.numLayers = numLayers;
        this.kvCachePool = kvCachePool;
        this.tokEmbeddings = new EmbeddingLayer(vocabSize, dim);

        this.layers = new ArrayList<>();
        MemorySegment moduleList = smile_module_list_create();
        for (int layerId = 0; layerId < numLayers; layerId++) {
            var block = new TransformerBlock(layerId, dim, numHeads, numKvHeads,
                    intermediateSize, multipleOf, ffnDimMultiplier, normEps, kvCachePool);
            this.layers.add(block);
            smile_module_list_push_back(moduleList, block.module);
        }

        this.norm = new RMSNormLayer(dim, normEps);
        this.output = new LinearLayer(dim, vocabSize, false);

        // Note that max_seq_len is multiplied by 2.
        this.cis = RotaryPositionalEncoding.computeFreqCis(
                dim / numHeads,
                maxSeqLen * 2,
                ropeTheta,
                scaledRope).to(device);

        MemorySegment listAsModule = smile_module_list_as_module(moduleList);
        add("layers", listAsModule);
        smile_module_free(listAsModule);
        smile_module_list_free(moduleList);
        add("tok_embeddings", tokEmbeddings);
        add("norm", norm);
        add("output", output);
        to(device);
    }

    /**
     * Returns the shared KV cache pool.
     * @return the KV cache pool.
     */
    public KvCachePool kvCachePool() {
        return kvCachePool;
    }

    /**
     * Replaces the shared KV cache pool on every attention layer.
     * Closes the previous pool. Intended to be called once after weight loading
     * so the pool can be sized from residual device memory.
     *
     * @param pool the new pool (must not be {@code null}).
     */
    public void setKvCachePool(KvCachePool pool) {
        setKvCachePool(pool, true);
    }

    /**
     * Replaces the shared KV cache pool on every attention layer.
     *
     * @param pool          the new pool (must not be {@code null}).
     * @param closePrevious when {@code true}, closes the previously installed pool.
     */
    public void setKvCachePool(KvCachePool pool, boolean closePrevious) {
        if (pool == null) throw new IllegalArgumentException("pool must not be null");
        if (pool.numLayers() < numLayers) {
            throw new IllegalArgumentException("pool.numLayers < model numLayers");
        }
        var previous = this.kvCachePool;
        this.kvCachePool = pool;
        for (var layer : layers) {
            if (layer.attention instanceof GroupedQueryAttention gqa) {
                gqa.setCachePool(pool);
            }
        }
        if (closePrevious && previous != null && previous != pool) {
            previous.close();
        }
    }

    /**
     * Forward pass through the model.
     * @param tokens the input token indices.
     * @param startPos the starting position for attention caching.
     * @return the output tensor.
     */
    public Tensor forward(Tensor tokens, int startPos) {
        long[] shape = tokens.shape();
        int seqlen = (int) shape[1];
        try (var scope = new AutoScope();
             var pos = Index.slice(startPos, startPos + seqlen)) {
            Tensor h = scope.add(tokEmbeddings.forward(tokens));
            Tensor freqs = scope.add(cis.get(pos));

            Tensor mask = null;
            if (seqlen > 1) {
                mask = scope.add(Tensor.full(Float.NEGATIVE_INFINITY, seqlen, seqlen));
                mask.triu_(1);
                try (var zeros = Tensor.zeros(seqlen, startPos)) {
                    mask = scope.add(Tensor.hstack(zeros, mask));
                }
                mask = scope.add(mask.to(h.dtype()));
            }

            for (var layer : layers) {
                h = scope.add(layer.forward(h, startPos, freqs, mask));
            }

            Tensor normalized = scope.add(norm.forward(h));
            return output.forward(normalized).to(ScalarType.Float);
        }
    }

    @Override
    public Tensor forward(Tensor tokens) {
        return forward(tokens, 0);
    }
}
