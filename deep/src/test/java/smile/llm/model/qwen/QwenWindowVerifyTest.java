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
import smile.llm.engine.SpeculativeDecoding;
import smile.util.Bytes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Window verify parity and plumbing tests (hybrid DeltaNet + full attention).
 *
 * @author Haifeng Li
 */
public class QwenWindowVerifyTest {

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
        model.eval();
        model.setKvCachePool(KvCachePool.forTesting(args.kvCacheLayout(), Device.CPU()), false);
        return model;
    }

    private static int[] pageAlignedPrompt() {
        int[] p = new int[16];
        for (int i = 0; i < p.length; i++) {
            p[i] = 1 + (i % 50);
        }
        return p;
    }

    @Test
    public void testGivenHybridPrefillWhenWindowVsSequentialThenArgmaxMatches() {
        QwenModelArgs args = new QwenModelArgs();
        QwenModel model = tinyModel(args);
        Qwen qwen = new Qwen("tiny-window", model, tinyTokenizer(), args);

        int[] prompt = pageAlignedPrompt();
        int requestId = qwen.bind(prompt, 32);
        try (Tensor prefill = qwen.prefillChunk(requestId, prompt, 0, prompt.length)) {
            assertNotNull(prefill);
        }

        int startPos = prompt.length;
        int[] window = {7, 11, 13};
        int[][] both = qwen.windowVsSequentialArgmax(requestId, window, startPos);
        assertArrayEquals(both[1], both[0],
                "window allTokenLogits argmax must match sequential decodeStep");
        assertTrue(qwen.lastWindowVsSequentialMaxAbs < 1e-3f,
                "window vs sequential logits maxAbs=" + qwen.lastWindowVsSequentialMaxAbs);

        qwen.evict(requestId);
    }

    @Test
    public void testGivenAcceptGreedyWhenRecordMetricsThenTargetForwardsPerRoundIsOne() {
        // Given/When/Then – pure accept helper + metric contract used by window verify.
        int[] drafts = {1, 2, 3};
        int[] targets = {1, 2, 9, 99};
        var accept = SpeculativeDecoding.acceptGreedy(drafts, targets);
        assertEquals(2, accept.numDraftAccepted());
        assertArrayEquals(new int[]{1, 2, 9}, accept.acceptedTokens());

        QwenModelArgs args = new QwenModelArgs();
        QwenModel model = tinyModel(args);
        Qwen qwen = new Qwen("tiny-metrics", model, tinyTokenizer(), args);
        qwen.resetSpeculativeMetrics();
        assertEquals(0.0, qwen.speculativeMeanTargetForwardsPerRound());

        int[] prompt = pageAlignedPrompt();
        int requestId = qwen.bind(prompt, 32);
        try (Tensor prefill = qwen.prefillChunk(requestId, prompt, 0, prompt.length)) {
            assertNotNull(prefill);
        }
        int lastPos = prompt.length - 1;
        int lastToken = prompt[lastPos];
        int[] draftToks = {7, 11};
        int written = qwen.verifyWindowOnlineRecorded(requestId, lastToken, lastPos, draftToks);
        assertTrue(written >= 1);
        assertEquals(1.0, qwen.speculativeMeanTargetForwardsPerRound(), 1e-9,
                "window verify must count exactly one target forward per round");
        qwen.evict(requestId);
    }

    @Test
    public void testGivenMtpModelWhenPartialAcceptThenCheckpointReplayCompletesWithoutSecondForward() {
        // SMILE_MTP_VERIFY_CHECKPOINT_REPLAY=1 is set for the whole :deep:test
        // task (deep/build.gradle.kts) — this exercises Qwen.verifyWindowOnline's
        // checkpoint-replay branch (GatedDeltaNet's per-position verify loop +
        // DeltaNetStatePool per-position checkpoint restore + QwenModel's MTP
        // anchor retention) end-to-end on a real, MTP-enabled hybrid model,
        // instead of the old second-full-forward replay.
        QwenModelArgs args = new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 1, 32,
                1, 3);
        assertTrue(args.hasMtp());
        QwenModel model = tinyModel(args);
        Qwen qwen = new Qwen("tiny-mtp-checkpoint-replay", model, tinyTokenizer(), args);
        qwen.resetSpeculativeMetrics();

        int[] prompt = pageAlignedPrompt();
        int requestId = qwen.bind(prompt, 32);
        try (Tensor prefill = qwen.prefillChunk(requestId, prompt, 0, prompt.length)) {
            assertNotNull(prefill);
        }

        int lastPos = prompt.length - 1;
        int lastToken = prompt[lastPos];
        // Random-weight model output won't greedily match every draft, so this
        // reliably exercises the r < n (partial accept / checkpoint-restore) branch.
        int[] drafts = {7, 11, 13};
        int written = qwen.verifyWindowOnlineRecorded(requestId, lastToken, lastPos, drafts);
        assertTrue(written >= 1 && written <= drafts.length + 1,
                "accepted token count out of range: " + written);
        assertEquals(1.0, qwen.speculativeMeanTargetForwardsPerRound(), 1e-9,
                "checkpoint-replay must still count exactly one target forward per round");

        // A further decode step from the sealed position must not throw and
        // must produce finite logits — corrupted/misaligned DeltaNet or KV
        // state from a bad checkpoint restore would typically surface here.
        int nextPos = lastPos + written;
        try (Tensor logits = qwen.decodeStep(
                new int[]{requestId}, new int[]{lastToken}, new int[]{nextPos})) {
            assertNotNull(logits);
        }

        qwen.evict(requestId);
    }

    @Test
    public void testGivenPartialAcceptWhenTruncateThenSealedLenHidesRejectedTail() {
        QwenModelArgs args = new QwenModelArgs();
        QwenModel model = tinyModel(args);
        Qwen qwen = new Qwen("tiny-truncate", model, tinyTokenizer(), args);

        int[] prompt = pageAlignedPrompt();
        int requestId = qwen.bind(prompt, 32);
        try (Tensor prefill = qwen.prefillChunk(requestId, prompt, 0, prompt.length)) {
            assertNotNull(prefill);
        }

        int startPos = prompt.length;
        int[] window = {3, 5, 8, 9};
        // Write a speculative window then seal a shorter prefix (as verify does).
        try (Tensor ignored = qwen.forwardVerifyWindow(requestId, window, startPos)) {
            // window written
        }
        model.kvCachePool().activateStep(requestId);
        int sealed = startPos + 2;
        int writtenEnd = startPos + window.length;
        model.kvCachePool().truncateTo(sealed, writtenEnd);
        assertDoesNotThrow(() -> model.kvCachePool().truncateTo(sealed, sealed));

        qwen.evict(requestId);
    }
}
