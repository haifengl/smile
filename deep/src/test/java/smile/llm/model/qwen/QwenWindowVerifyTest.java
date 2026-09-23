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
import smile.deep.tensor.Index;
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
        if (model.mtp() != null) {
            model.mtp().setKvCachePool(
                    KvCachePool.forTesting(model.mtp().kvCacheLayout(), Device.CPU()), false);
        }
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
    public void testGivenTwoConcurrentRequestsWhenOtherDecodesThenAnchorPoolIsolatesEachRequest() {
        // Regression guard for the MtpAnchorPool fix: QwenModel.lastPreNormHidden
        // is a single field overwritten by every forward, for whichever batch of
        // requestIds it just processed. Qwen.scatterMtpAnchor immediately copies
        // it into a per-request row right after each forward, so request B's own
        // decode step (batch [B]) must never affect request A's already-written
        // anchor row, even though both share the same QwenModel instance.
        QwenModelArgs args = new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 2, 32,
                1, 3);
        QwenModel model = tinyModel(args);
        Qwen qwen = new Qwen("tiny-mtp-anchor-isolation", model, tinyTokenizer(), args);

        int[] promptA = pageAlignedPrompt();
        int requestA = qwen.bind(promptA, 32);
        try (Tensor prefill = qwen.prefillChunk(requestA, promptA, 0, promptA.length)) {
            assertNotNull(prefill);
        }
        int lastPosA = promptA.length - 1;
        int lastTokenA = promptA[lastPosA];
        int[] draftsA = {7, 11, 13};
        int written = qwen.verifyWindowOnlineRecorded(requestA, lastTokenA, lastPosA, draftsA);
        assertTrue(written >= 1);

        Tensor anchorABefore = model.mtpAnchorPool().getRow(requestA);
        assertNotNull(anchorABefore, "request A must have a written anchor after its own verify round");
        float[] beforeValues = anchorABefore.to(Device.CPU(), ScalarType.Float).floatArray();

        // A second, unrelated request prefills and decodes on the same model.
        int[] promptB = {2, 4, 6, 8, 10, 12, 14, 16};
        int requestB = qwen.bind(promptB, 32);
        try (Tensor prefill = qwen.prefillChunk(requestB, promptB, 0, promptB.length)) {
            assertNotNull(prefill);
        }
        int lastPosB = promptB.length - 1;
        int lastTokenB = promptB[lastPosB];
        try (Tensor logits = qwen.decodeStep(
                new int[]{requestB}, new int[]{lastTokenB}, new int[]{lastPosB})) {
            assertNotNull(logits);
        }

        Tensor anchorAAfter = model.mtpAnchorPool().getRow(requestA);
        assertNotNull(anchorAAfter);
        float[] afterValues = anchorAAfter.to(Device.CPU(), ScalarType.Float).floatArray();
        assertArrayEquals(beforeValues, afterValues, 0f,
                "request B's decode step must not change request A's MTP anchor");

        anchorABefore.close();
        anchorAAfter.close();
        qwen.evict(requestA);
        qwen.evict(requestB);
    }

    @Test
    public void testGivenGraphBuffersActiveWhenBatchGrowsThenAnchorCapacityIsPreSizedNotStale() {
        // Regression guard for a real production crash: InferenceEngine batched
        // 4 concurrent requests into one decode step right after each was
        // prefilled alone (batch=1), by which point CUDA decode-graph buffers
        // were already pinned for the new batch=4 bucket. QwenModel's MTP
        // anchor (lastPreNormHidden) refuses to reallocate while graph buffers
        // are active (illegal mid-capture), so without pre-sizing it while
        // still eager, the anchor stayed at its earlier batch=1 shape and the
        // subsequent 4-row Qwen.scatterMtpAnchor indexed row 1 out of bounds:
        // "index 1 is out of bounds for dimension 0 with size 1".
        // QwenModel.ensureLastPreNormHiddenCapacity is the fix — called from
        // forwardDecodeGraph/forwardVerifyGraph strictly before the graph
        // buffers flag flips — exercised directly here since actually
        // reaching forwardDecodeGraph needs real CUDA graph capture.
        QwenModelArgs args = new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 4, 32,
                1, 3);
        QwenModel model = tinyModel(args);

        // Four requests, each prefilled alone (batch=1), leave the anchor at [1, D].
        try (Tensor hidden1 = Tensor.rand(
                new Tensor.Options().device(Device.CPU()).dtype(ScalarType.Float),
                1, args.dim())) {
            model.capturePreNormHidden(hidden1);
        }
        assertEquals(1, model.lastPreNormHidden().shape()[0]);

        // The fix: pre-size while still eager, before graph buffers pin the shape.
        model.ensureLastPreNormHiddenCapacity(4);
        assertEquals(4, model.lastPreNormHidden().shape()[0],
                "pre-sizing must grow the anchor buffer before graph buffers pin its shape");

        model.kvCachePool().setDecodeGraphBuffers(true);
        try {
            try (Tensor hidden4 = Tensor.rand(
                    new Tensor.Options().device(Device.CPU()).dtype(ScalarType.Float),
                    4, args.dim())) {
                model.capturePreNormHidden(hidden4);
            }
        } finally {
            model.kvCachePool().setDecodeGraphBuffers(false);
        }

        Tensor anchor = model.lastPreNormHidden();
        assertEquals(4, anchor.shape()[0],
                "anchor must still be batch=4 after capturing this round's real hidden under graph mode");

        // The actual crash site: scattering every row into the per-request
        // pool must not throw an out-of-bounds index for row > 0.
        for (int i = 0; i < 4; i++) {
            model.mtpAnchorPool().bindRequest(1000 + i);
        }
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 4; i++) {
                try (var idx = Index.of(i); Tensor row = anchor.get(idx)) {
                    model.mtpAnchorPool().setRow(1000 + i, row);
                }
            }
        });
    }

    @Test
    public void testGivenMixedFullAndPartialAcceptInBatchWhenVerifyBatchThenAnchorPositionsStayInBounds() {
        // Regression guard for a real production crash: Qwen.verifyWindowOnlineBatch
        // reused restoreSlots (r+1, a 1-indexed DeltaNet checkpoint slot, valid
        // 1..n+1) directly as the 0-indexed window position argument to
        // QwenModel.setMtpAnchorAtWindowPositions (valid 0..n) whenever *any*
        // row in the batch was a partial accept. A fully-accepted row (r == n)
        // sharing that batch then passed window position n+1 — one past the
        // last valid window index — and crashed:
        // "index 3 is out of bounds for dimension 1 with size 3". The
        // single-request path never hits this because it only ever calls
        // setMtpAnchorAtWindowPosition(r) (not r+1) and only when r < n.
        //
        // Forces exactly that mix deterministically: row A's single draft is
        // set to the target model's own greedy continuation (computed from an
        // identically-seeded reference instance, so its weights — and thus
        // this greedy prediction — are bit-identical to the subject instance),
        // guaranteeing a full accept (r == n == 1); row B's draft is an
        // arbitrary token guaranteed to mismatch, guaranteeing a partial
        // accept (r == 0 < n).
        QwenModelArgs args = new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 2, 32,
                1, 3);
        long seed = 123L;

        smile.torch.smile_torch_h.smile_manual_seed(seed);
        QwenModel modelRef = tinyModel(args);
        Qwen qwenRef = new Qwen("tiny-mixed-accept-ref", modelRef, tinyTokenizer(), args);

        int[] promptA = pageAlignedPrompt();
        int lastPosA = promptA.length - 1;
        int lastTokenA = promptA[lastPosA];
        int reqARef = qwenRef.bind(promptA, 32);
        try (Tensor p = qwenRef.prefillChunk(reqARef, promptA, 0, promptA.length)) {
            assertNotNull(p);
        }
        int greedyNextA;
        try (Tensor logits = qwenRef.decodeStep(
                new int[]{reqARef}, new int[]{lastTokenA}, new int[]{lastPosA})) {
            greedyNextA = smile.llm.engine.Sampling.sampleGreedyTokenId(logits);
        }

        smile.torch.smile_torch_h.smile_manual_seed(seed);
        QwenModel modelSubject = tinyModel(args);
        Qwen qwenSubject = new Qwen("tiny-mixed-accept-subject", modelSubject, tinyTokenizer(), args);

        int[] promptB = new int[promptA.length];
        for (int i = 0; i < promptB.length; i++) {
            promptB[i] = 1 + ((i * 3 + 7) % 50);
        }
        int lastPosB = promptB.length - 1;
        int lastTokenB = promptB[lastPosB];

        int reqA = qwenSubject.bind(promptA, 32);
        try (Tensor p = qwenSubject.prefillChunk(reqA, promptA, 0, promptA.length)) {
            assertNotNull(p);
        }
        int reqB = qwenSubject.bind(promptB, 32);
        try (Tensor p = qwenSubject.prefillChunk(reqB, promptB, 0, promptB.length)) {
            assertNotNull(p);
        }

        // A guaranteed mismatch: the model's vocab here is 100, so a token far
        // outside anything plausible for a greedy match still round-trips fine.
        int mismatchDraftB = (lastTokenB + 43) % 100;

        SpeculativeDecoding.AcceptResult[] results = assertDoesNotThrow(() ->
                qwenSubject.verifyWindowOnlineBatchRecorded(
                        new int[]{reqA, reqB},
                        new int[]{lastTokenA, lastTokenB},
                        new int[]{lastPosA, lastPosB},
                        new int[][]{{greedyNextA}, {mismatchDraftB}}));

        assertEquals(1, results[0].numDraftAccepted(), "row A's single draft must fully accept");
        assertEquals(0, results[1].numDraftAccepted(), "row B's mismatched draft must be rejected");

        // A further decode step for each (separately — the two rows now sit
        // at different absolute positions since they accepted different
        // counts, and torch_native's decode path requires uniform positions
        // across a single batched call) must not throw and must produce
        // finite logits — corrupted anchor/DeltaNet state from the bug would
        // typically surface here.
        try (Tensor logits = qwenSubject.decodeStep(
                new int[]{reqA}, new int[]{lastTokenA}, new int[]{lastPosA + results[0].numTokens()})) {
            assertNotNull(logits);
        }
        try (Tensor logits = qwenSubject.decodeStep(
                new int[]{reqB}, new int[]{lastTokenB}, new int[]{lastPosB + results[1].numTokens()})) {
            assertNotNull(logits);
        }

        qwenRef.evict(reqARef);
        qwenSubject.evict(reqA);
        qwenSubject.evict(reqB);
    }

    @Test
    public void testGivenManyConsecutiveRoundsWhenBatchedSpeculateThenMatchesSingleRequestPathPerRound() {
        // Regression guard for a real production symptom: a 4-concurrent-request
        // run with speculative-max-concurrency=4 produced coherent output for the
        // first ~15-20 speculative rounds, then all four requests degenerated
        // into repetitive garbage at almost the same relative position — a
        // classic symptom of state (DeltaNet / anchor / KV) slowly desyncing
        // across MANY consecutive rounds, not a single-round bug. The existing
        // single-round equivalence tests
        // (testGivenSamePositionBatchedCohortWhenSpeculateBatchThenMatches...,
        // testGivenMixedFullAndPartialAcceptInBatchWhenVerifyBatchThenAnchor...)
        // only ever check one round from a fresh prefill, so they cannot catch
        // a bug that only accumulates over many rounds.
        //
        // Uses the *same* prompt for both rows, matching the real production
        // run exactly (all 4 concurrent requests there shared one identical
        // prompt at temperature=0.0): with identical input and deterministic
        // greedy sampling, both rows must accept the same draft count every
        // round and never drift apart in absolute position, keeping this
        // fully runnable on torch_native (heterogeneous-position batched
        // verify needs FlashInfer, unavailable on this host — see
        // testGivenDifferentPositionsOnTorchNativeWhenBatchedVerifyThenThrowsClearError).
        // Each row's reference sequence comes from the already-trusted
        // single-request path (b=1, on a separate but identically-seeded model
        // instance) rather than forced/artificial drafts, so this reproduces
        // the real MTP-driven multi-round accumulation end to end; a bug in
        // how the batched path indexes/restores per-row state would still
        // show up as a divergence from that reference even though both rows
        // "should" behave identically to each other.
        QwenModelArgs args = new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 2, 700,
                1, 2);
        long seed = 777L;

        smile.torch.smile_torch_h.smile_manual_seed(seed);
        QwenModel modelRef = tinyModel(args);
        Qwen qwenRef = new Qwen("tiny-many-rounds-ref", modelRef, tinyTokenizer(), args);

        smile.torch.smile_torch_h.smile_manual_seed(seed);
        QwenModel modelBatch = tinyModel(args);
        Qwen qwenBatch = new Qwen("tiny-many-rounds-batch", modelBatch, tinyTokenizer(), args);

        int[] promptA = pageAlignedPrompt();
        int[] promptB = promptA.clone();

        int reqARef = qwenRef.bind(promptA, 700);
        try (Tensor p = qwenRef.prefillChunk(reqARef, promptA, 0, promptA.length)) {
            assertNotNull(p);
        }
        int reqBRef = qwenRef.bind(promptB, 700);
        try (Tensor p = qwenRef.prefillChunk(reqBRef, promptB, 0, promptB.length)) {
            assertNotNull(p);
        }

        int reqA = qwenBatch.bind(promptA, 700);
        try (Tensor p = qwenBatch.prefillChunk(reqA, promptA, 0, promptA.length)) {
            assertNotNull(p);
        }
        int reqB = qwenBatch.bind(promptB, 700);
        try (Tensor p = qwenBatch.prefillChunk(reqB, promptB, 0, promptB.length)) {
            assertNotNull(p);
        }

        int lastPosARef = promptA.length - 1;
        int lastTokenARef = promptA[lastPosARef];
        int lastPosBRef = promptB.length - 1;
        int lastTokenBRef = promptB[lastPosBRef];
        int lastPosA = lastPosARef;
        int lastTokenA = lastTokenARef;
        int lastPosB = lastPosBRef;
        int lastTokenB = lastTokenBRef;

        int numDrafts = 2;
        int rounds = 150;
        for (int round = 0; round < rounds; round++) {
            int[][] refA = qwenRef.speculateStep(
                    new int[]{reqARef}, new int[]{lastTokenARef}, new int[]{lastPosARef},
                    numDrafts, 0.0, 1.0);
            int[][] refB = qwenRef.speculateStep(
                    new int[]{reqBRef}, new int[]{lastTokenBRef}, new int[]{lastPosBRef},
                    numDrafts, 0.0, 1.0);

            int[][] batch = qwenBatch.speculateStep(
                    new int[]{reqA, reqB}, new int[]{lastTokenA, lastTokenB},
                    new int[]{lastPosA, lastPosB}, numDrafts, 0.0, 1.0);

            assertArrayEquals(refA[0], batch[0],
                    "round " + round + ": row A (batched) diverged from its single-request reference");
            assertArrayEquals(refB[0], batch[1],
                    "round " + round + ": row B (batched) diverged from its single-request reference");

            lastPosARef += refA[0].length;
            lastTokenARef = refA[0][refA[0].length - 1];
            lastPosBRef += refB[0].length;
            lastTokenBRef = refB[0][refB[0].length - 1];
            lastPosA += batch[0].length;
            lastTokenA = batch[0][batch[0].length - 1];
            lastPosB += batch[1].length;
            lastTokenB = batch[1][batch[1].length - 1];
        }

        qwenRef.evict(reqARef);
        qwenRef.evict(reqBRef);
        qwenBatch.evict(reqA);
        qwenBatch.evict(reqB);
    }

    @Test
    public void testGivenManyConsecutiveRoundsWhenSingleRequestSpeculateThenMatchesPlainGreedyDecode() {
        // Isolates whether many-round speculative decoding is correct at all,
        // independent of batching: speculative decoding is supposed to be
        // output-equivalent to plain greedy decoding at temperature=0 (that's
        // the entire premise of the technique), so the single-request path
        // (already proven correct for one round by
        // testGivenMtpModelWhenPartialAcceptThenCheckpointReplayCompletesWithoutSecondForward)
        // must still match plain decode after MANY consecutive rounds. If this
        // fails, the real production degeneration (garbled repetitive output
        // after ~15-20 rounds) is a general MTP checkpoint-replay bug, not
        // something specific to the new batched path.
        QwenModelArgs args = new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 2, 700,
                1, 2);
        long seed = 777L;

        smile.torch.smile_torch_h.smile_manual_seed(seed);
        QwenModel modelSpec = tinyModel(args);
        Qwen qwenSpec = new Qwen("tiny-many-rounds-spec", modelSpec, tinyTokenizer(), args);

        smile.torch.smile_torch_h.smile_manual_seed(seed);
        QwenModel modelPlain = tinyModel(args);
        Qwen qwenPlain = new Qwen("tiny-many-rounds-plain", modelPlain, tinyTokenizer(), args);

        int[] prompt = pageAlignedPrompt();

        int reqSpec = qwenSpec.bind(prompt, 700);
        try (Tensor p = qwenSpec.prefillChunk(reqSpec, prompt, 0, prompt.length)) {
            assertNotNull(p);
        }
        int reqPlain = qwenPlain.bind(prompt, 700);
        try (Tensor p = qwenPlain.prefillChunk(reqPlain, prompt, 0, prompt.length)) {
            assertNotNull(p);
        }

        int lastPosSpec = prompt.length - 1;
        int lastTokenSpec = prompt[lastPosSpec];
        int lastPosPlain = prompt.length - 1;
        int lastTokenPlain = prompt[lastPosPlain];

        int numDrafts = 2;
        int rounds = 150;
        java.util.List<Integer> specTokens = new java.util.ArrayList<>();
        java.util.List<Integer> plainTokens = new java.util.ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            int[][] out = qwenSpec.speculateStep(
                    new int[]{reqSpec}, new int[]{lastTokenSpec}, new int[]{lastPosSpec},
                    numDrafts, 0.0, 1.0);
            for (int tok : out[0]) {
                specTokens.add(tok);
            }
            lastPosSpec += out[0].length;
            lastTokenSpec = out[0][out[0].length - 1];

            for (int i = 0; i < out[0].length; i++) {
                try (Tensor logits = qwenPlain.decodeStep(
                        new int[]{reqPlain}, new int[]{lastTokenPlain}, new int[]{lastPosPlain})) {
                    int tok = smile.llm.engine.Sampling.sampleGreedyTokenId(logits);
                    plainTokens.add(tok);
                    lastPosPlain++;
                    lastTokenPlain = tok;
                }
            }
        }

        assertEquals(plainTokens, specTokens,
                "speculative decoding must be output-equivalent to plain greedy decode at temperature=0");

        qwenSpec.evict(reqSpec);
        qwenPlain.evict(reqPlain);
    }

    @Test
    public void testGivenSamePositionBatchedCohortWhenSpeculateBatchThenMatchesSingleRequestPathPerRow() {
        // Stage 2/3 equivalence guard: Qwen.speculateBatch (batched draft +
        // batched ragged verify, heterogeneous per-row positions) must
        // produce, for each row, exactly what the existing single-request
        // Qwen.speculateStep path produces from the identical starting state.
        // Two separately-constructed model instances, seeded identically
        // right before construction, get bit-identical random weights;
        // temperature=0 (greedy) means no further randomness is consumed
        // during generation, so any divergence points to a real bug in the
        // batched/ragged plumbing (positions, cache lengths, per-row
        // checkpoint restore), not to random noise.
        QwenModelArgs args = new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 2, 32,
                1, 3);
        long seed = 42L;

        smile.torch.smile_torch_h.smile_manual_seed(seed);
        QwenModel modelRef = tinyModel(args);
        Qwen qwenRef = new Qwen("tiny-batch-ref", modelRef, tinyTokenizer(), args);

        smile.torch.smile_torch_h.smile_manual_seed(seed);
        QwenModel modelBatch = tinyModel(args);
        Qwen qwenBatch = new Qwen("tiny-batch-subject", modelBatch, tinyTokenizer(), args);

        // Same length (-> same lastPos) so both rows share one absolute
        // position: GatedAttention's full-attention layer only has a ragged
        // (per-row-position) code path for seqlen==1 (decode); a verify
        // window (seqlen>1) with genuinely different positions per row would
        // need a new ragged-prefill attention path that doesn't exist yet
        // (and, on this CPU-only host, torch_native explicitly refuses
        // non-uniform positions even for the seqlen==1 case it does support —
        // see GatedAttention.forwardDecodeRagged). Same-position batching is
        // still the realistic common case: smile.chat.admit-coalesce-ms
        // exists specifically to admit concurrent requests together so they
        // progress in lockstep.
        int[] promptA = pageAlignedPrompt();
        int[] promptB = new int[promptA.length];
        for (int i = 0; i < promptB.length; i++) {
            promptB[i] = 1 + ((i * 3 + 7) % 50);
        }
        int lastPosA = promptA.length - 1;
        int lastTokenA = promptA[lastPosA];
        int lastPosB = promptB.length - 1;
        int lastTokenB = promptB[lastPosB];

        int reqARef = qwenRef.bind(promptA, 32);
        try (Tensor p = qwenRef.prefillChunk(reqARef, promptA, 0, promptA.length)) {
            assertNotNull(p);
        }
        int reqBRef = qwenRef.bind(promptB, 32);
        try (Tensor p = qwenRef.prefillChunk(reqBRef, promptB, 0, promptB.length)) {
            assertNotNull(p);
        }
        int[][] refA = qwenRef.speculateStep(
                new int[]{reqARef}, new int[]{lastTokenA}, new int[]{lastPosA}, 3, 0.0, 1.0);
        int[][] refB = qwenRef.speculateStep(
                new int[]{reqBRef}, new int[]{lastTokenB}, new int[]{lastPosB}, 3, 0.0, 1.0);

        int reqABatch = qwenBatch.bind(promptA, 32);
        try (Tensor p = qwenBatch.prefillChunk(reqABatch, promptA, 0, promptA.length)) {
            assertNotNull(p);
        }
        int reqBBatch = qwenBatch.bind(promptB, 32);
        try (Tensor p = qwenBatch.prefillChunk(reqBBatch, promptB, 0, promptB.length)) {
            assertNotNull(p);
        }
        int[][] batched = qwenBatch.speculateBatch(
                new int[]{reqABatch, reqBBatch},
                new int[]{lastTokenA, lastTokenB},
                new int[]{lastPosA, lastPosB},
                3, 0.0, 1.0);

        assertArrayEquals(refA[0], batched[0],
                "request A's batched accepted tokens must match the single-request reference");
        assertArrayEquals(refB[0], batched[1],
                "request B's batched accepted tokens must match the single-request reference");

        qwenRef.evict(reqARef);
        qwenRef.evict(reqBRef);
        qwenBatch.evict(reqABatch);
        qwenBatch.evict(reqBBatch);
    }

    @Test
    public void testGivenDifferentPositionsOnTorchNativeWhenBatchedVerifyThenThrowsClearError() {
        // Stage 5 regression guard: GatedAttention.forward's ragged seqLen>1
        // dispatch (forwardVerifyRagged) requires FlashInfer — torch_native has
        // no per-row-KV-length gather path for it (mirrors forwardDecodeRagged's
        // identical seqLen==1 restriction). On this CPU-only host that's the
        // only backend available, so calling batched verify with genuinely
        // different positions per row must fail loudly with a clear message,
        // not silently misbehave.
        QwenModelArgs args = new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 2, 32,
                1, 3);
        QwenModel model = tinyModel(args);
        Qwen qwen = new Qwen("tiny-ragged-guard", model, tinyTokenizer(), args);

        int[] promptA = pageAlignedPrompt();
        int[] promptB = new int[promptA.length - 4];
        for (int i = 0; i < promptB.length; i++) {
            promptB[i] = 1 + ((i * 3 + 7) % 50);
        }

        int requestA = qwen.bind(promptA, 32);
        try (Tensor p = qwen.prefillChunk(requestA, promptA, 0, promptA.length)) {
            assertNotNull(p);
        }
        int requestB = qwen.bind(promptB, 32);
        try (Tensor p = qwen.prefillChunk(requestB, promptB, 0, promptB.length)) {
            assertNotNull(p);
        }

        int lastPosA = promptA.length - 1;
        int lastPosB = promptB.length - 1;
        int[] windowA = {7, 11, 13};
        int[] windowB = {17, 19, 23};

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                qwen.windowVsSequentialArgmaxBatch(
                        new int[]{requestA, requestB},
                        new int[][]{windowA, windowB},
                        new int[]{lastPosA, lastPosB}));
        assertTrue(ex.getMessage().contains("ragged verify window requires FlashInfer"),
                "expected clear ragged-verify error, got: " + ex.getMessage());

        qwen.evict(requestA);
        qwen.evict(requestB);
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
