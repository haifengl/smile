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
import smile.llm.transformer.ModelArgs;
import smile.llm.transformer.Transformer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code smile.llm.llama} package.
 * Use small architectures (tiny dim/layers) so they run quickly on CPU
 * without requiring a checkpoint file or the Llama tokenizer model.
 *
 * @author Haifeng Li
 */
public class LlamaTest {

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



