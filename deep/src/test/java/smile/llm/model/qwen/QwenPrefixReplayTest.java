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
package smile.llm.model.qwen;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCachePool;
import smile.util.Bytes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-1 hybrid prefix reuse: KV pages shared; DeltaNet restored via warmPrefix.
 */
public class QwenPrefixReplayTest {

    private static Tokenizer tinyTokenizer() {
        Map<Bytes, Integer> ranks = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            ranks.put(new Bytes(new byte[]{(byte) i}), i);
        }
        return new Tokenizer(ranks);
    }

    private static QwenModel tinyModel(QwenModelArgs args) {
        DeltaNetStatePool statePool = new DeltaNetStatePool(
                args.numLinearAttentionLayers(),
                args.linearNumValueHeads(),
                args.linearKeyHeadDim(),
                args.linearValueHeadDim(),
                args.linearConvDim(),
                args.linearConvKernelDim(),
                Math.max(2, args.maxBatchSize()),
                Device.CPU(),
                ScalarType.Float);
        QwenModel model = new QwenModel(args, statePool);
        model.to(Device.CPU());
        model.setKvCachePool(KvCachePool.forTesting(args.kvCacheLayout(), Device.CPU()), false);
        return model;
    }

    /** Page-aligned prompt (pageSize=16) within tiny maxSeqLen=32. */
    private static int[] pageAlignedPrompt() {
        int[] p = new int[16];
        for (int i = 0; i < p.length; i++) {
            p[i] = 1 + (i % 50);
        }
        return p;
    }

    private static float[] copyRecurrentRow0(DeltaNetStatePool pool, int requestId) {
        pool.activateStep(requestId);
        Tensor layer0 = pool.recurrent(0); // pool-owned; do not close
        try (var row = smile.deep.tensor.Index.of(0);
             Tensor r0 = layer0.get(row);
             Tensor owned = r0.copy()) {
            return owned.floatArray();
        }
    }

    @Test
    public void testGivenHybridWithoutReplayWhenEnablePrefixThenForcedOff() {
        QwenModelArgs args = new QwenModelArgs();
        QwenModel model = tinyModel(args);
        Qwen qwen = new Qwen("tiny", model, tinyTokenizer(), args);
        assertFalse(qwen.isPrefixReplayEnabled());
        qwen.setPrefixReuseEnabled(true);
        assertFalse(model.kvCachePool().isPrefixReuseEnabled());
    }

    @Test
    public void testGivenHybridWithReplayWhenSecondBindThenPrefixHitAndStateMatchesCold() {
        // Given – page-aligned prompt; maxBatchSize >= 2 for two concurrent bindings
        QwenModelArgs base = new QwenModelArgs();
        QwenModelArgs args = new QwenModelArgs(
                base.dim(), base.numLayers(), base.numHeads(), base.numKvHeads(), base.headDim(),
                base.vocabSize(), base.intermediateSize(), base.normEps(), base.ropeTheta(),
                base.partialRotaryFactor(), base.linearConvKernelDim(),
                base.linearKeyHeadDim(), base.linearValueHeadDim(),
                base.linearNumKeyHeads(), base.linearNumValueHeads(),
                base.layerTypes(), 2, base.maxSeqLen());
        QwenModel model = tinyModel(args);
        model.eval();
        Qwen qwen = new Qwen("tiny", model, tinyTokenizer(), args);
        qwen.setPrefixReplayEnabled(true);
        qwen.setPrefixReuseEnabled(true);
        assertTrue(model.kvCachePool().isPrefixReuseEnabled());

        int[] prompt = pageAlignedPrompt();
        int capacity = 32;

        // Cold reference: bind without relying on radix, full prefill
        int coldId = qwen.bind(prompt, capacity);
        assertEquals(0, qwen.prefixLen(coldId));
        Tensor coldLogits = qwen.prefillChunk(coldId, prompt, 0, prompt.length);
        assertNotNull(coldLogits);
        coldLogits.close();
        float[] coldState = copyRecurrentRow0(model.deltaNetStatePool(), coldId);
        int[] sequence = new int[prompt.length];
        System.arraycopy(prompt, 0, sequence, 0, prompt.length);
        qwen.finish(coldId, sequence);

        // When – second bind hits radix; warmPrefix restores DeltaNet
        int hitId = qwen.bind(prompt, capacity);
        int matched = qwen.prefixLen(hitId);
        assertEquals(prompt.length, matched, "expected full page-aligned prefix hit");
        qwen.warmPrefix(hitId, prompt, matched);
        float[] warmState = copyRecurrentRow0(model.deltaNetStatePool(), hitId);

        // Then – DeltaNet recurrent state matches cold prefill
        assertEquals(coldState.length, warmState.length);
        float maxAbs = 0f;
        for (int i = 0; i < coldState.length; i++) {
            maxAbs = Math.max(maxAbs, Math.abs(coldState[i] - warmState[i]));
        }
        assertTrue(maxAbs < 1e-4f, "DeltaNet state mismatch after warmPrefix, maxAbs=" + maxAbs);

        qwen.evict(hitId);
    }
}
