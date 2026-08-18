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

import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCacheLayout;
import smile.llm.cache.KvCachePool;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code smile.llm.transformer} package.
 *
 * <p>Tests cover {@link FeedForward}, {@link Attention},
 * {@link TransformerBlock}, and {@link Transformer} using
 * small architectures (tiny dim/layers) so they run quickly on CPU without
 * requiring a checkpoint file or the Llama tokenizer model.
 *
 * @author Haifeng Li
 */
public class TransformerTest {

    private static final int DIM = 64;
    private static final int NUM_HEADS = 4;
    private static final int NUM_KV_HEADS = 4;
    private static final int VOCAB = 100;
    private static final int MULTIPLE_OF = 256;
    private static final double NORM_EPS = 1e-5;
    private static final double ROPE_THETA = 10000.0;

    private static KvCacheLayout layout(int layers, int batch, int seq) {
        return KvCacheLayout.of(layers, DIM, NUM_HEADS, NUM_KV_HEADS, batch, seq);
    }

    private static Transformer tinyTransformer(int layers, int batch, int seq) {
        return new Transformer(DIM, layers, NUM_HEADS, NUM_KV_HEADS, VOCAB,
                null, MULTIPLE_OF, null, NORM_EPS, ROPE_THETA, false, seq,
                layout(layers, batch, seq), Device.CPU());
    }

    // -----------------------------------------------------------------------
    // FeedForward — hidden dimension calculation
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFeedForwardWithNoMultiplierWhenCreatedThenForwardOutputDimMatchesInput() {
        // hiddenDim = 4*64=256 → 2/3: 170 → round up to multiple of 256 → 256
        // w2 maps hidden→dim, so output of forward should have last dim=64
        FeedForward ff = new FeedForward(64, 4 * 64, 256, null);
        Tensor x = Tensor.ones(1, 2, 64);
        Tensor out = ff.forward(x);
        assertEquals(64, out.shape()[2], "Output last dim should equal input dim");
        x.close(); out.close();
    }

    @Test
    public void testGivenFeedForwardWithMultiplierWhenCreatedThenForwardOutputDimMatchesInput() {
        // multiplier=1.3 → hidden=221 → round to 256; output dim must still be 64
        FeedForward ff = new FeedForward(64, 4 * 64, 256, 1.3);
        Tensor x = Tensor.ones(1, 2, 64);
        Tensor out = ff.forward(x);
        assertEquals(64, out.shape()[2]);
        x.close(); out.close();
    }

    @Test
    public void testGivenFeedForwardWhenForwardCalledThenOutputShapeMatchesInput() {
        // dim=64, hiddenDim calculation gives some multiple of 256
        FeedForward ff = new FeedForward(64, 4 * 64, 256, null);
        Tensor x = Tensor.ones(1, 4, 64);
        Tensor out = ff.forward(x);
        assertArrayEquals(new long[]{1, 4, 64}, out.shape());
        x.close(); out.close();
    }

    // -----------------------------------------------------------------------
    // Attention — construction and field values
    // -----------------------------------------------------------------------

    @Test
    public void testGivenAttentionWhenConstructedThenHeadDimIsCorrect() {
        GroupedQueryAttention attn = new GroupedQueryAttention(
                DIM, NUM_HEADS, NUM_KV_HEADS, layout(1, 1, 32));
        assertEquals(DIM / NUM_HEADS, attn.headDim);
    }

    @Test
    public void testGivenAttentionWhenConstructedThenUsesSharedKvCachePool() {
        // maxBatchSize=2, maxSeqLen=32, numLocalKvHeads=4, headDim=16
        var layout = layout(1, 2, 32);
        var pool = KvCachePool.forTesting(layout, Device.CPU());
        GroupedQueryAttention attn = new GroupedQueryAttention(DIM, NUM_HEADS, NUM_KV_HEADS, pool, 0);
        assertEquals(2 * 32, pool.numSlots());
        assertEquals(0, attn.layerId);
        assertEquals(16, attn.headDim);
        assertEquals(4, attn.numKvHeads);
        pool.close();
    }

    @Test
    public void testGivenGqaConfigWhenAttentionCreatedThenNumRepIsCorrect() {
        // 4 query heads, 2 KV heads → numRep = 2
        var layout = KvCacheLayout.of(1, DIM, 4, 2, 1, 64);
        GroupedQueryAttention attn = new GroupedQueryAttention(DIM, 4, 2, layout);
        assertEquals(2, attn.numKvHeads);
        assertEquals(2, attn.numRep);
    }

    @Test
    public void testGivenMatchingHeadsWhenAttentionCreatedThenNumRepIsOne() {
        GroupedQueryAttention attn = new GroupedQueryAttention(
                DIM, NUM_HEADS, NUM_KV_HEADS, layout(1, 1, 64));
        assertEquals(NUM_HEADS, attn.numKvHeads);
        assertEquals(1, attn.numRep);
    }

    // -----------------------------------------------------------------------
    // TransformerBlock — construction
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTransformerBlockWhenConstructedThenLayerIdIsSet() {
        TransformerBlock block = new TransformerBlock(
                1, DIM, NUM_HEADS, NUM_KV_HEADS, null, MULTIPLE_OF, null, NORM_EPS, layout(2, 1, 32));
        assertEquals(1, block.layerId);
        assertEquals(64, block.dim);
        assertEquals(4, block.numHeads);
        assertEquals(16, block.headDim);
    }

    @Test
    public void testGivenTransformerBlockWhenForwardCalledThenOutputShapeMatchesInput() {
        // Tiny: dim=64, 1 head, 1 layer, batch=1, seqlen=4
        TransformerBlock block = new TransformerBlock(
                0, DIM, NUM_HEADS, NUM_KV_HEADS, null, MULTIPLE_OF, null, NORM_EPS, layout(1, 1, 32));
        // Precompute CIS for the block
        Tensor cis = RotaryPositionalEncoding.computeFreqCis(16, 32 * 2);
        Tensor freqs = cis.get(smile.deep.tensor.Index.slice(0, 4));
        Tensor x = Tensor.randn(1, 4, 64);
        Tensor out = block.forward(x, 0, freqs, null);
        assertArrayEquals(new long[]{1, 4, 64}, out.shape());
        x.close(); out.close(); cis.close(); freqs.close();
    }

    // -----------------------------------------------------------------------
    // Transformer — construction and forward pass
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTransformerWhenConstructedThenVocabSizeAndLayersAreSet() {
        Transformer transformer = tinyTransformer(2, 1, 32);
        assertEquals(100, transformer.vocabSize);
        assertEquals(2, transformer.numLayers);
        assertEquals(2, transformer.layers.size());
    }

    @Test
    public void testGivenTransformerWhenForwardCalledThenOutputShapeIsCorrect() {
        // output shape: [batchSize, seqLen, vocabSize]
        Transformer transformer = tinyTransformer(1, 1, 32);
        transformer.eval();
        // Token IDs in [0, vocabSize)
        Tensor tokens = Tensor.of(new long[]{1L, 2L, 3L, 4L}, 1, 4);
        Tensor out = transformer.forward(tokens);
        assertArrayEquals(new long[]{1, 4, 100}, out.shape(),
                "Output shape should be [batch, seqLen, vocabSize]");
        tokens.close(); out.close();
    }

    @Test
    public void testGivenTransformerWhenForwardCalledWithSingleTokenThenNoMaskApplied() {
        // seqLen=1 → mask is null (no causal masking needed)
        Transformer transformer = tinyTransformer(1, 1, 32);
        transformer.eval();
        Tensor tokens = Tensor.of(new long[]{5L}, 1, 1);
        Tensor out = transformer.forward(tokens, 0);
        assertArrayEquals(new long[]{1, 1, 100}, out.shape());
        tokens.close(); out.close();
    }

    @Test
    public void testGivenTransformerWhenForwardCalledWithMultipleTokensThenMaskIsBuilt() {
        // seqLen>1 → causal mask is created; this should not throw
        Transformer transformer = tinyTransformer(1, 1, 32);
        transformer.eval();
        Tensor tokens = Tensor.of(new long[]{1L, 2L, 3L}, 1, 3);
        assertDoesNotThrow(() -> {
            Tensor out = transformer.forward(tokens, 0);
            out.close();
        });
        tokens.close();
    }
}
