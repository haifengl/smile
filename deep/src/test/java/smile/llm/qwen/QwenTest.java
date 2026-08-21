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

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.cache.KvCachePool;
import smile.util.Bytes;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the Qwen hybrid stack (tiny random weights on CPU).
 *
 * @author Haifeng Li
 */
public class QwenTest {

    private static Tokenizer tinyTokenizer() {
        Map<Bytes, Integer> ranks = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            ranks.put(new Bytes(new byte[]{(byte) i}), i);
        }
        return new Tokenizer(ranks);
    }

    /** Tiny CPU model with test-sized DeltaNet + KV pools. */
    private static QwenModel tinyModel(QwenModelArgs args) {
        DeltaNetStatePool statePool = args.numLinearAttentionLayers() > 0
                ? new DeltaNetStatePool(
                args.numLinearAttentionLayers(),
                args.linearNumValueHeads(),
                args.linearKeyHeadDim(),
                args.linearValueHeadDim(),
                args.linearConvDim(),
                args.linearConvKernelDim(),
                args.maxBatchSize(),
                Device.CPU(),
                smile.deep.tensor.ScalarType.Float)
                : null;
        QwenModel model = new QwenModel(args, statePool);
        model.to(Device.CPU());
        if (args.numFullAttentionLayers() > 0) {
            model.setKvCachePool(KvCachePool.forTesting(args.kvCacheLayout(), Device.CPU()), false);
        }
        return model;
    }

    @Test
    public void testGivenTinyArgsWhenModelConstructedThenLayerCountsMatch() {
        QwenModelArgs args = new QwenModelArgs();
        QwenModel model = tinyModel(args);
        assertEquals(4, model.numLayers);
        assertEquals(100, model.vocabSize);
        assertEquals(1, model.kvCachePool().numLayers());
        assertEquals(3, model.deltaNetStatePool().numLinearLayers());
        assertEquals("alibaba/qwen3.5", Qwen.family);
    }

    @Test
    public void testGivenTinyModelWhenForwardCalledThenLogitsShapeIsCorrect() {
        QwenModelArgs args = new QwenModelArgs();
        QwenModel model = tinyModel(args);
        model.eval();
        if (model.deltaNetStatePool() != null) {
            model.deltaNetStatePool().reset(1);
        }
        if (model.kvCachePool() != null) {
            model.kvCachePool().bindRequests(1, 8);
        }
        try {
            Tensor tokens = Tensor.of(new long[]{1L, 2L, 3L, 4L}, 1, 4);
            Tensor out = model.forward(tokens, 0, true);
            assertArrayEquals(new long[]{1, 4, 100}, out.shape());
            tokens.close();
            out.close();
        } finally {
            if (model.kvCachePool() != null) {
                model.kvCachePool().unbindRequests();
            }
        }
    }

    @Test
    public void testGivenGenerateWithTooManyPromptsThenThrows() {
        QwenModelArgs args = new QwenModelArgs(); // maxBatch=1
        QwenModel model = tinyModel(args);
        Qwen qwen = new Qwen("tiny", model, tinyTokenizer(), args);
        int[][] prompts = {{1, 2}, {3, 4}};
        assertThrows(IllegalArgumentException.class,
                () -> qwen.generate(prompts, 4, 0.0, 0.9, false, 0, null));
    }

    @Test
    public void testGivenGreedyGenerateThenCompletionReturned() {
        QwenModelArgs args = new QwenModelArgs();
        QwenModel model = tinyModel(args);
        model.eval();
        Qwen qwen = new Qwen("tiny", model, tinyTokenizer(), args);
        int[][] prompts = {{1, 2, 3}};
        var results = qwen.generate(prompts, 4, 0.0, 0.9, false, 42, null);
        assertNotNull(results);
        assertEquals(1, results.length);
        assertNotNull(results[0]);
    }

    @Test
    public void testGivenDialogWhenEncodedThenContainsImTokens() {
        Tokenizer tok = tinyTokenizer();
        int[] ids = tok.encodeDialog(new Message(Role.user, "hi"));
        assertTrue(ids.length > 0);
        Integer imStart = tok.specialToken("<|im_start|>");
        assertNotNull(imStart);
        boolean found = false;
        for (int id : ids) {
            if (id == imStart) found = true;
        }
        assertTrue(found);
    }
}
