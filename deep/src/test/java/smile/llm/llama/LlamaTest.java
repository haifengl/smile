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

import java.io.IOException;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code smile.llm.llama} package.
 *
 * <p>Tests cover {@link ModelArgs}, {@link FeedForward}, {@link Attention},
 * {@link TransformerBlock}, {@link Transformer}, and {@link Llama} using
 * small architectures (tiny dim/layers) so they run quickly on CPU without
 * requiring a checkpoint file or the Llama tokenizer model.
 *
 * @author Haifeng Li
 */
public class LlamaTest {

    // -----------------------------------------------------------------------
    // ModelArgs — default constructor
    // -----------------------------------------------------------------------

    @Test
    public void testGivenDefaultModelArgsWhenCreatedThenValuesAreStandardDefaults() {
        ModelArgs args = new ModelArgs();
        assertEquals(4096, args.dim());
        assertEquals(32, args.numLayers());
        assertEquals(32, args.numHeads());
        assertNull(args.numKvHeads());
        assertEquals(-1, args.vocabSize());
        assertEquals(256, args.multipleOf(), "multipleOf should default to 256, not 356");
        assertNull(args.ffnDimMultiplier());
        assertEquals(1e-5, args.normEps(), 1e-10);
        assertEquals(500000.0, args.ropeTheta(), 1.0);
        assertFalse(args.scaledRope());
        assertEquals(32, args.maxBatchSize());
        assertEquals(2048, args.maxSeqLen());
    }

    // -----------------------------------------------------------------------
    // ModelArgs — from JSON (full params)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFullParamsJsonWhenLoadedThenAllFieldsPopulated() throws IOException {
        ModelArgs args = ModelArgs.from("deep/src/test/resources/llama/params.json", 4, 512);
        assertEquals(512, args.dim());
        assertEquals(2, args.numLayers());
        assertEquals(8, args.numHeads());
        assertEquals(4, args.numKvHeads());
        assertEquals(1000, args.vocabSize());
        assertEquals(256, args.multipleOf());
        assertNotNull(args.ffnDimMultiplier());
        assertEquals(1.3, args.ffnDimMultiplier(), 1e-9);
        assertEquals(1e-5, args.normEps(), 1e-12);
        assertEquals(500000.0, args.ropeTheta(), 1.0);
        assertFalse(args.scaledRope());
        assertEquals(4, args.maxBatchSize());
        assertEquals(512, args.maxSeqLen());
    }

    @Test
    public void testGivenMinimalParamsJsonWhenLoadedThenOptionalFieldsAreNull() throws IOException {
        // params_minimal.json omits n_kv_heads, ffn_dim_multiplier, rope_theta, use_scaled_rope
        ModelArgs args = ModelArgs.from("deep/src/test/resources/llama/params_minimal.json", 2, 128);
        assertEquals(512, args.dim());
        assertNull(args.numKvHeads(), "n_kv_heads absent → should be null");
        assertNull(args.ffnDimMultiplier(), "ffn_dim_multiplier absent → should be null");
        assertEquals(10000.0, args.ropeTheta(), 1.0, "rope_theta absent → default 10000.0");
        assertFalse(args.scaledRope(), "use_scaled_rope absent → default false");
    }

    @Test
    public void testGivenNonexistentParamsFileWhenLoadedThenThrowsIOException() {
        assertThrows(IOException.class,
                () -> ModelArgs.from("nonexistent/params.json", 1, 128));
    }

    // -----------------------------------------------------------------------
    // ModelArgs — numKvHeads fallback in Attention
    // -----------------------------------------------------------------------

    @Test
    public void testGivenNullNumKvHeadsWhenAttentionCreatedThenFallsBackToNumHeads() {
        // numKvHeads=null → Attention should fall back to numHeads (no GQA)
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 64);
        Attention attn = new Attention(args);
        assertEquals(args.numHeads(), attn.numKvHeads,
                "numKvHeads should equal numHeads when numKvHeads param is null");
        assertEquals(1, attn.numRep, "numRep should be 1 when numKvHeads == numHeads");
    }

    @Test
    public void testGivenGqaConfigWhenAttentionCreatedThenNumRepIsCorrect() {
        // 4 query heads, 2 KV heads → numRep = 2
        ModelArgs args = new ModelArgs(64, 1, 4, 2, 100, 256, null, 1e-5, 10000.0, false, 1, 64);
        Attention attn = new Attention(args);
        assertEquals(2, attn.numKvHeads);
        assertEquals(2, attn.numRep);
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
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 32);
        Attention attn = new Attention(args);
        assertEquals(64 / 4, attn.headDim);  // dim / numHeads
    }

    @Test
    public void testGivenAttentionWhenConstructedThenCachesHaveCorrectShape() {
        // maxBatchSize=2, maxSeqLen=32, numLocalKvHeads=4, headDim=16
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 2, 32);
        Attention attn = new Attention(args);
        assertArrayEquals(new long[]{2, 32, 4, 16}, attn.cacheK.shape());
        assertArrayEquals(new long[]{2, 32, 4, 16}, attn.cacheV.shape());
    }

    // -----------------------------------------------------------------------
    // TransformerBlock — construction
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTransformerBlockWhenConstructedThenLayerIdIsSet() {
        ModelArgs args = new ModelArgs(64, 2, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 32);
        TransformerBlock block = new TransformerBlock(1, args);
        assertEquals(1, block.layerId);
        assertEquals(64, block.dim);
        assertEquals(4, block.numHeads);
        assertEquals(16, block.headDim);
    }

    @Test
    public void testGivenTransformerBlockWhenForwardCalledThenOutputShapeMatchesInput() {
        // Tiny: dim=64, 1 head, 1 layer, batch=1, seqlen=4
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 32);
        TransformerBlock block = new TransformerBlock(0, args);
        // Precompute CIS for the block
        Tensor cis = smile.llm.RotaryPositionalEncoding.computeFreqCis(16, 32 * 2);
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
        ModelArgs args = new ModelArgs(64, 2, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 32);
        Transformer transformer = new Transformer(args, Device.CPU());
        assertEquals(100, transformer.vocabSize);
        assertEquals(2, transformer.numLayers);
        assertEquals(2, transformer.layers.size());
    }

    @Test
    public void testGivenTransformerWhenForwardCalledThenOutputShapeIsCorrect() {
        // output shape: [batchSize, seqLen, vocabSize]
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 32);
        Transformer transformer = new Transformer(args, Device.CPU());
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
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 32);
        Transformer transformer = new Transformer(args, Device.CPU());
        transformer.eval();
        Tensor tokens = Tensor.of(new long[]{5L}, 1, 1);
        Tensor out = transformer.forward(tokens, 0);
        assertArrayEquals(new long[]{1, 1, 100}, out.shape());
        tokens.close(); out.close();
    }

    @Test
    public void testGivenTransformerWhenForwardCalledWithMultipleTokensThenMaskIsBuilt() {
        // seqLen>1 → causal mask is created; this should not throw
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 32);
        Transformer transformer = new Transformer(args, Device.CPU());
        transformer.eval();
        Tensor tokens = Tensor.of(new long[]{1L, 2L, 3L}, 1, 3);
        assertDoesNotThrow(() -> {
            Tensor out = transformer.forward(tokens, 0);
            out.close();
        });
        tokens.close();
    }

    // -----------------------------------------------------------------------
    // Llama — toString, family, name
    // -----------------------------------------------------------------------

    @Test
    public void testGivenLlamaFamilyConstantThenIsStaticFinalString() {
        // Confirm the field is accessible statically and has expected value
        assertEquals("meta/llama3", Llama.family);
    }

    @Test
    public void testGivenLlamaWhenToStringCalledThenFormatIsCorrect() {
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 32);
        Transformer transformer = new Transformer(args, Device.CPU());
        Tokenizer tokenizer = createTinyTokenizer();
        Llama llama = new Llama("llama3-tiny", transformer, tokenizer);
        assertEquals("meta/llama3/llama3-tiny", llama.toString());
        assertEquals("meta/llama3", llama.family());
        assertEquals("llama3-tiny", llama.name());
    }

    @Test
    public void testGivenLlamaBuildWithNonexistentDirThenThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> Llama.build("nonexistent/dir", "tokenizer.model", 1, 128, (byte) -1));
    }

    // -----------------------------------------------------------------------
    // Llama.generate — validation
    // -----------------------------------------------------------------------

    @Test
    public void testGivenGenerateWithTooManyPromptsThenThrowsIllegalArgument() {
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 32);
        Transformer transformer = new Transformer(args, Device.CPU());
        Tokenizer tokenizer = createTinyTokenizer();
        Llama llama = new Llama("test", transformer, tokenizer);
        // maxBatchSize=1, but we pass 2 prompts
        int[][] prompts = {{1, 2}, {3, 4}};
        assertThrows(IllegalArgumentException.class,
                () -> llama.generate(prompts, 5, 0.0, 0.9, false, 0, null));
    }

    @Test
    public void testGivenGenerateWithPromptTooLongThenThrowsIllegalArgument() {
        // maxSeqLen=8, prompt length=10
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 8);
        Transformer transformer = new Transformer(args, Device.CPU());
        Tokenizer tokenizer = createTinyTokenizer();
        Llama llama = new Llama("test", transformer, tokenizer);
        int[][] prompts = {new int[10]};  // prompt length 10 > maxSeqLen 8
        assertThrows(IllegalArgumentException.class,
                () -> llama.generate(prompts, 5, 0.0, 0.9, false, 0, null));
    }

    @Test
    public void testGivenGenerateWithPublisherAndBatchSizeGtOneThenThrowsIllegalArgument() {
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 2, 32);
        Transformer transformer = new Transformer(args, Device.CPU());
        Tokenizer tokenizer = createTinyTokenizer();
        Llama llama = new Llama("test", transformer, tokenizer);
        int[][] prompts = {{1}, {2}};
        var publisher = new java.util.concurrent.SubmissionPublisher<String>();
        assertThrows(IllegalArgumentException.class,
                () -> llama.generate(prompts, 5, 0.0, 0.9, false, 0, publisher));
        publisher.close();
    }

    @Test
    public void testGivenGenerateWithGreedyDecodingThenCompletionIsReturned() {
        // Small enough to run on CPU quickly: dim=64, 1 layer, vocab=100, maxSeqLen=16
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 16);
        Transformer transformer = new Transformer(args, Device.CPU());
        Tokenizer tokenizer = createTinyTokenizer();
        Llama llama = new Llama("test", transformer, tokenizer);
        transformer.eval();
        int[][] prompts = {{1, 2, 3}};  // prompt of 3 tokens
        var results = llama.generate(prompts, 4, 0.0, 0.9, false, 42, null);
        assertNotNull(results);
        assertEquals(1, results.length);
        assertNotNull(results[0]);
    }

    // -----------------------------------------------------------------------
    // Helper — create a tiny tokenizer with ranks for IDs 0..99
    // -----------------------------------------------------------------------

    /**
     * Builds a tiny Llama-compatible tokenizer for unit tests.
     * The tokenizer has vocab IDs 0–99 as single bytes, plus all required
     * Llama 3 special tokens.
     */
    private static Tokenizer createTinyTokenizer() {
        java.util.Map<smile.util.Bytes, Integer> ranks = new java.util.HashMap<>();
        for (int i = 0; i < 100; i++) {
            ranks.put(new smile.util.Bytes(new byte[]{(byte) i}), i);
        }
        return new Tokenizer(ranks);
    }
}



