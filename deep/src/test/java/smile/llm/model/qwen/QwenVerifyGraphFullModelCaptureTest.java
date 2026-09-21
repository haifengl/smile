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
import smile.llm.attention.AttentionBackend;
import smile.llm.attention.AttentionBackends;
import smile.llm.cache.KvCacheLayout;
import smile.llm.cache.KvCachePool;
import smile.util.Bytes;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reproduces, in isolation, the real-hardware Stage 5 corruption (captured
 * verify graph replay returning a different — sometimes crashing — tensor)
 * with the two already-ruled-out variables removed: no tensor parallelism (a
 * single GPU, single thread, no {@code ExecutorService} futures — concurrent
 * multi-rank capture was serialized and still failed) and DeltaNet's own
 * {@code S > 1} forward already proven safe in isolation
 * ({@code GatedDeltaNetCudaGraphTest}). What's left, untested until now: the
 * <em>full</em> hybrid model forward — alternating full-attention and
 * DeltaNet layers, the real {@code lm_head} at production vocab size
 * (~250k), {@code capturePreNormHidden} — all captured together in one graph,
 * with the verify-capturable attention kernel invoked once per full-attention
 * layer within that single capture (Stage 2's own kernel test only ever
 * captured one call to it).
 *
 * <p>Drives the exact same code path production uses
 * ({@code Qwen.forwardVerifyWindow}'s {@code scatter == false} branch →
 * {@code forwardWindowVerify} → {@code QwenModel.forwardVerifyGraph}) via the
 * already-existing {@link Qwen#windowVsSequentialArgmax} test helper — called
 * repeatedly with the identical window/{@code startPos} so the same
 * {@code (batch, windowLen, numPages)} bucket warms up, captures once, then
 * replays on every subsequent call, comparing the window forward against an
 * independently-computed sequential-decode reference every single time
 * (not just once).
 *
 * @author Haifeng Li
 */
public class QwenVerifyGraphFullModelCaptureTest {

    private static final int ROUNDS = 15;

    /** Realistic page size (matches production's ~16, not forTesting's degenerate pageSize=1). */
    private static final int PAGE_SIZE = 16;

    private static KvCachePool kvCachePoolWithRealisticPageSize(KvCacheLayout layout, Device device) {
        int numSlots = layout.maxBatchSize() * layout.maxSeqLen();
        return new KvCachePool(layout.numLayers(), numSlots, layout.numKvHeads(), layout.headDim(),
                PAGE_SIZE, device, ScalarType.Float);
    }

    private static boolean cudaAvailable() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0;
    }

    private static Tokenizer tinyTokenizer() {
        Map<Bytes, Integer> ranks = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            ranks.put(new Bytes(new byte[]{(byte) i}), i);
        }
        return new Tokenizer(ranks);
    }

    private static int[] pageAlignedPrompt() {
        int[] p = new int[16];
        for (int i = 0; i < p.length; i++) {
            p[i] = 1 + (i % 50);
        }
        return p;
    }

    @Test
    public void testGivenHybridModelOnCudaWhenVerifyGraphReplayedManyTimesThenMatchesSequentialDecode() {
        assumeTrue(cudaAvailable(), "CUDA not available in this environment");
        assumeTrue(smile.torch.Native.cudaGraphAvailable(), "smile_cuda_graph_* not in libsmile_torch");

        AttentionBackend previousBackend = AttentionBackends.current();
        AttentionBackends.install(AttentionBackend.FLASHINFER);
        assumeTrue(AttentionBackends.current() == AttentionBackend.FLASHINFER,
                "FlashInfer not compiled into libsmile_torch");
        try {
            Device device = Device.CUDA();

            // Same shape as QwenWindowVerifyTest's own proven CPU config (4 hybrid
            // layers: 3 linear-attention + 1 full-attention), except vocabSize is
            // bumped to match real production (~250k) — the one dimension neither
            // Stage 1/2's kernel-only test nor GatedDeltaNetCudaGraphTest's
            // tiny-hidden test ever allocated at this scale inside a captured region.
            String[] types = QwenModelArgs.defaultLayerTypes(4, 4);
            QwenModelArgs args = new QwenModelArgs(
                    64,     // dim
                    4,      // numLayers
                    4,      // numHeads
                    2,      // numKvHeads
                    64,     // headDim — real FlashInfer decode kernel only supports
                            // 64/128/256/512; 16 silently falls back to gather+SDPA
                    248320, // vocabSize — matches the real model that crashed
                    128,    // intermediateSize
                    1e-6,   // normEps
                    10000.0, // ropeTheta
                    0.25,   // partialRotaryFactor
                    4,      // linearConvKernelDim
                    16,     // linearKeyHeadDim
                    16,     // linearValueHeadDim
                    2,      // linearNumKeyHeads
                    4,      // linearNumValueHeads
                    types,
                    1,      // maxBatchSize
                    32      // maxSeqLen
            );

            DeltaNetStatePool statePool = new DeltaNetStatePool(
                    args.numLinearAttentionLayers(), args.linearNumValueHeads(),
                    args.linearKeyHeadDim(), args.linearValueHeadDim(),
                    args.linearConvDim(), args.linearConvKernelDim(),
                    Math.max(2, args.maxBatchSize()), device, ScalarType.Float);
            QwenModel model = new QwenModel(args, statePool);
            model.to(device);
            model.eval();
            // KvCachePool.forTesting hardcodes pageSize=1 (fine for CPU-only
            // plumbing tests, but a degenerate case — every token its own page
            // — that neither Stage 1/2's kernel tests (pageSize=8) nor
            // production (pageSize~16) ever exercise with the verify-capturable
            // kernel). Build the pool directly with a realistic page size.
            model.setKvCachePool(kvCachePoolWithRealisticPageSize(args.kvCacheLayout(), device), false);

            Qwen qwen = new Qwen("cuda-verify-graph-full-model", model, tinyTokenizer(), args);

            int[] prompt = pageAlignedPrompt();
            int requestId = qwen.bind(prompt, 32);
            try (Tensor prefill = qwen.prefillChunk(requestId, prompt, 0, prompt.length)) {
                assertNotNull(prefill);
            }

            int startPos = prompt.length;
            int[] window = {7, 11, 13};

            // Calling this repeatedly with the identical (startPos, window) drives
            // VerifyCudaGraphSession through the same real sequence production
            // hits: 2 eager warmup rounds, one real capture, then real replay on
            // every round after — windowVsSequentialArgmax always fully "rejects"
            // (truncateKv back to startPos, restoring DeltaNet), so every round
            // re-exercises the identical grow(+3)/shrink(reject-all) bucket.
            for (int round = 0; round < ROUNDS; round++) {
                // Deliberately no exact-argmax assertion here (unlike
                // QwenWindowVerifyTest's small-vocab original, which this test
                // otherwise mirrors): with random, untrained weights and a
                // ~250k-entry vocab, the top logits are frequently near-tied, so
                // a legitimate, tiny floating-point difference between the
                // window-verify prefill kernel and the sequential decode kernel
                // can flip an argmax that is essentially a coin flip, without
                // indicating any real numerical divergence. The logit-magnitude
                // diff below is the real, robust correctness signal.
                qwen.windowVsSequentialArgmax(requestId, window, startPos);
                System.out.println("round " + round + ": maxAbs=" + qwen.lastWindowVsSequentialMaxAbs);
                assertTrue(qwen.lastWindowVsSequentialMaxAbs < 1e-2f,
                        "round " + round + ": window vs sequential logits maxAbs="
                                + qwen.lastWindowVsSequentialMaxAbs);
            }

            qwen.evict(requestId);
        } finally {
            AttentionBackends.install(previousBackend);
        }
    }
}
