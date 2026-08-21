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
package smile.llm.llama;

import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.attention.AttentionBackend;
import smile.llm.attention.AttentionBackends;
import smile.llm.cache.KvCacheLayout;
import smile.llm.cache.KvCachePool;
import smile.llm.transformer.RotaryPositionalEncoding;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GroupedQueryAttention}, {@link LlamaBlock}, and {@link LlamaModel}.
 *
 * @author Haifeng Li
 */
public class LlamaModelTest {

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

    private static LlamaModel tinyModel(int layers, int batch, int seq) {
        LlamaModel model = new LlamaModel(DIM, layers, NUM_HEADS, NUM_KV_HEADS, VOCAB,
                null, MULTIPLE_OF, null, NORM_EPS, ROPE_THETA, false, seq);
        model.to(Device.CPU());
        model.setKvCachePool(KvCachePool.forTesting(layout(layers, batch, seq), Device.CPU()), false);
        return model;
    }

    @BeforeEach
    public void installTorchNativeAttention() {
        // Other suites may leave FLASHINFER installed; unit tests use contiguous SDPA.
        AttentionBackends.install(AttentionBackend.TORCH_NATIVE);
    }

    @Test
    public void testGivenAttentionWhenConstructedThenHeadDimIsCorrect() {
        GroupedQueryAttention attn = new GroupedQueryAttention(
                DIM, NUM_HEADS, NUM_KV_HEADS, layout(1, 1, 32));
        assertEquals(DIM / NUM_HEADS, attn.headDim);
    }

    @Test
    public void testGivenAttentionWhenConstructedThenUsesSharedKvCachePool() {
        var layout = layout(1, 2, 32);
        var pool = KvCachePool.forTesting(layout, Device.CPU());
        GroupedQueryAttention attn = new GroupedQueryAttention(DIM, NUM_HEADS, NUM_KV_HEADS, 0);
        attn.setCachePool(pool);
        assertEquals(2 * 32, pool.numSlots());
        assertEquals(0, attn.layerId);
        assertEquals(16, attn.headDim);
        assertEquals(4, attn.numKvHeads);
        pool.close();
    }

    @Test
    public void testGivenGqaConfigWhenAttentionCreatedThenNumRepIsCorrect() {
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

    @Test
    public void testGivenLlamaBlockWhenConstructedThenLayerIdIsSet() {
        LlamaBlock block = new LlamaBlock(
                1, DIM, NUM_HEADS, NUM_KV_HEADS, null, MULTIPLE_OF, null, NORM_EPS, layout(2, 1, 32));
        assertEquals(1, block.layerId);
        assertEquals(64, block.dim);
        assertEquals(4, block.numHeads);
        assertEquals(16, block.headDim);
    }

    @Test
    public void testGivenLlamaBlockWhenForwardCalledThenOutputShapeMatchesInput() {
        LlamaBlock block = new LlamaBlock(
                0, DIM, NUM_HEADS, NUM_KV_HEADS, null, MULTIPLE_OF, null, NORM_EPS, layout(1, 1, 32));
        KvCachePool pool = ((GroupedQueryAttention) block.attention).cachePool;
        pool.bindRequests(1, 32);
        try {
            Tensor cis = RotaryPositionalEncoding.computeFreqCis(16, 32 * 2);
            Tensor freqs = cis.get(smile.deep.tensor.Index.slice(0, 4));
            Tensor x = Tensor.randn(1, 4, 64);
            Tensor out = block.forward(x, 0, freqs, null);
            assertArrayEquals(new long[]{1, 4, 64}, out.shape());
            x.close(); out.close(); cis.close(); freqs.close();
        } finally {
            pool.unbindRequests();
        }
    }

    @Test
    public void testGivenLlamaModelWhenConstructedThenVocabSizeAndLayersAreSet() {
        LlamaModel model = tinyModel(2, 1, 32);
        assertEquals(100, model.vocabSize);
        assertEquals(2, model.numLayers);
        assertEquals(2, model.layers.size());
    }

    @Test
    public void testGivenLlamaModelWhenForwardCalledThenOutputShapeIsCorrect() {
        LlamaModel model = tinyModel(1, 1, 32);
        model.eval();
        model.kvCachePool().bindRequests(1, 32);
        try {
            Tensor tokens = Tensor.of(new long[]{1L, 2L, 3L, 4L}, 1, 4);
            Tensor out = model.forward(tokens);
            assertArrayEquals(new long[]{1, 4, 100}, out.shape(),
                    "Output shape should be [batch, seqLen, vocabSize]");
            tokens.close(); out.close();
        } finally {
            model.kvCachePool().unbindRequests();
        }
    }

    @Test
    public void testGivenLlamaModelWhenForwardCalledWithSingleTokenThenNoMaskApplied() {
        LlamaModel model = tinyModel(1, 1, 32);
        model.eval();
        model.kvCachePool().bindRequests(1, 32);
        try {
            Tensor tokens = Tensor.of(new long[]{5L}, 1, 1);
            Tensor out = model.forward(tokens, 0);
            assertArrayEquals(new long[]{1, 1, 100}, out.shape());
            tokens.close(); out.close();
        } finally {
            model.kvCachePool().unbindRequests();
        }
    }

    @Test
    public void testGivenLlamaModelWhenForwardCalledWithMultipleTokensThenMaskIsBuilt() {
        LlamaModel model = tinyModel(1, 1, 32);
        model.eval();
        model.kvCachePool().bindRequests(1, 32);
        try {
            Tensor tokens = Tensor.of(new long[]{1L, 2L, 3L}, 1, 3);
            assertDoesNotThrow(() -> {
                Tensor out = model.forward(tokens, 0);
                out.close();
            });
            tokens.close();
        } finally {
            model.kvCachePool().unbindRequests();
        }
    }
}
