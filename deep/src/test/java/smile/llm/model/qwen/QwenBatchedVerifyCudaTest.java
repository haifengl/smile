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
import org.junit.jupiter.api.Tag;
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
 * Proves {@code Qwen.verifyWindowOnlineBatch}'s real FlashInfer path — two
 * concurrent requests at the same absolute position verified in <em>one</em>
 * batched forward — matches each request's own sequential-decode reference,
 * on real CUDA hardware with the exact configuration
 * {@code QwenVerifyGraphFullModelCaptureTest} already found necessary to
 * avoid silently falling back to a mask-less SDPA path: {@code headDim=64}
 * (FlashInfer's decode/paged kernel only supports 64/128/256/512) and bf16
 * compute (the kernel dispatch requires bf16/fp16 query).
 *
 * <p>This is the batched counterpart of that test's own
 * {@code windowVsSequentialArgmax} comparison, now for
 * {@code windowVsSequentialArgmaxBatch}, and deliberately reuses its
 * logit-magnitude-diff methodology rather than argmax-only comparison — see
 * that test's javadoc for why (a coin-flip argmax on random/untrained
 * weights is not evidence of a real numeric problem, but a growing raw logit
 * gap is).
 *
 * @author Haifeng Li
 */
@Tag("cuda")
public class QwenBatchedVerifyCudaTest {

    private static final int ROUNDS = 10;
    private static final int PAGE_SIZE = 16;

    private static KvCachePool kvCachePoolWithRealisticPageSize(KvCacheLayout layout, Device device) {
        int numSlots = layout.maxBatchSize() * layout.maxSeqLen();
        return new KvCachePool(layout.numLayers(), numSlots, layout.numKvHeads(), layout.headDim(),
                PAGE_SIZE, device, ScalarType.BFloat16);
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

    private static int[] pageAlignedPrompt(int seed) {
        int[] p = new int[16];
        for (int i = 0; i < p.length; i++) {
            p[i] = 1 + ((i * 3 + seed) % 50);
        }
        return p;
    }

    @Test
    public void testGivenTwoConcurrentRequestsOnCudaWhenBatchedVerifyThenMatchesSequentialDecodePerRow() {
        assumeTrue(cudaAvailable(), "CUDA not available in this environment");

        AttentionBackend previousBackend = AttentionBackends.current();
        AttentionBackends.install(AttentionBackend.FLASHINFER);
        assumeTrue(AttentionBackends.current() == AttentionBackend.FLASHINFER,
                "FlashInfer not compiled into libsmile_torch");
        try {
            Device device = Device.CUDA();

            // Same shape as QwenVerifyGraphFullModelCaptureTest's own hard-won
            // config, except maxBatchSize=2 (two concurrent requests) instead
            // of 1, and no MTP head (this test targets the batched *verify*
            // forward directly via windowVsSequentialArgmaxBatch, not the
            // draft head — same as the single-request test it mirrors).
            String[] types = QwenModelArgs.defaultLayerTypes(4, 4);
            QwenModelArgs args = new QwenModelArgs(
                    64,     // dim
                    4,      // numLayers
                    4,      // numHeads
                    2,      // numKvHeads
                    64,     // headDim — 16/128/256/512 only; 64 avoids the silent
                            // mask-less SDPA fallback QwenVerifyGraphFullModelCaptureTest found
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
                    2,      // maxBatchSize — two concurrent requests
                    32      // maxSeqLen
            );

            DeltaNetStatePool statePool = new DeltaNetStatePool(
                    args.numLinearAttentionLayers(), args.linearNumValueHeads(),
                    args.linearKeyHeadDim(), args.linearValueHeadDim(),
                    args.linearConvDim(), args.linearConvKernelDim(),
                    args.maxBatchSize(), device, ScalarType.Float);
            QwenModel model = new QwenModel(args, statePool);
            model.to(device, ScalarType.BFloat16);
            model.eval();
            model.setKvCachePool(kvCachePoolWithRealisticPageSize(args.kvCacheLayout(), device), false);

            Qwen qwen = new Qwen("cuda-batched-verify", model, tinyTokenizer(), args);

            int[] promptA = pageAlignedPrompt(0);
            int[] promptB = pageAlignedPrompt(7);
            int requestA = qwen.bind(promptA, 32);
            try (Tensor prefill = qwen.prefillChunk(requestA, promptA, 0, promptA.length)) {
                assertNotNull(prefill);
            }
            int requestB = qwen.bind(promptB, 32);
            try (Tensor prefill = qwen.prefillChunk(requestB, promptB, 0, promptB.length)) {
                assertNotNull(prefill);
            }

            int startPos = promptA.length;
            int[] windowA = {7, 11, 13};
            int[] windowB = {17, 19, 23};

            // Repeated with the identical (startPos, window) per row, mirroring
            // QwenVerifyGraphFullModelCaptureTest's own rationale: re-exercise
            // the identical grow(+3)/shrink(reject-all) bucket every round
            // (windowVsSequentialArgmaxBatch always fully "rejects", restoring
            // DeltaNet/KV back to startPos after each comparison).
            for (int round = 0; round < ROUNDS; round++) {
                qwen.windowVsSequentialArgmaxBatch(
                        new int[]{requestA, requestB},
                        new int[][]{windowA, windowB},
                        new int[]{startPos, startPos});
                System.out.println("round " + round + ": maxAbs=" + qwen.lastWindowVsSequentialMaxAbs);
                assertTrue(qwen.lastWindowVsSequentialMaxAbs < 5e-2f,
                        "round " + round + ": batched-window vs sequential logits maxAbs="
                                + qwen.lastWindowVsSequentialMaxAbs);
            }

            qwen.evict(requestA);
            qwen.evict(requestB);
        } finally {
            AttentionBackends.install(previousBackend);
        }
    }
}
