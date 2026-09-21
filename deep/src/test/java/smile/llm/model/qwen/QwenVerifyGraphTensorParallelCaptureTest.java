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
import smile.deep.CUDA;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.attention.AttentionBackend;
import smile.llm.attention.AttentionBackends;
import smile.llm.cache.KvCacheLayout;
import smile.llm.cache.KvCachePool;
import smile.llm.parallel.ParallelConfig;
import smile.llm.parallel.TensorParallelGroup;
import smile.llm.parallel.TensorShardSpec;
import smile.torch.Native;
import smile.util.Bytes;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The decisive test: {@link QwenVerifyGraphFullModelCaptureTest} proved the
 * full hybrid model — real vocab size, real {@code lm_head}, alternating
 * attention/DeltaNet layers — captures and replays correctly on a single
 * GPU. Every production failure so far has used real tensor parallelism
 * (4 GPUs, NCCL all-reduce inside the captured region); a capture-region
 * serialization fix across TP ranks did not help, and was removed after
 * this test's own single-GPU sibling proved capture concurrency was never
 * the actual variable. The one remaining untested dimension is TP itself:
 * does capturing an NCCL all-reduce for a multi-token (S &gt; 1) verify
 * forward work at all, independent of the full 4-GPU production scale?
 *
 * <p>Mirrors {@code Qwen.constructRank}'s real production shard-construction
 * sequence (see {@code Qwen.java}: {@code TensorShardSpec.forRank},
 * per-rank {@code DeltaNetStatePool} sized via {@code linearConvDim(shard)},
 * per-rank {@code KvCachePool} sized via {@code kvCacheLayout(shard)}) with
 * {@code tpSize = 2} — the smallest configuration that actually exercises a
 * real NCCL communicator and a real cross-device all-reduce, rather than
 * inventing shard/TP wiring from scratch.
 *
 * @author Haifeng Li
 */
public class QwenVerifyGraphTensorParallelCaptureTest {

    private static final int ROUNDS = 15;
    private static final int TP_SIZE = 2;
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
    public void testGivenTp2ModelWhenVerifyGraphReplayedManyTimesThenMatchesSequentialDecode() {
        assumeTrue(cudaAvailable(), "CUDA not available in this environment");
        assumeTrue(CUDA.deviceCount() >= TP_SIZE, "requires >= " + TP_SIZE + " CUDA devices");
        assumeTrue(Native.cudaGraphAvailable(), "smile_cuda_graph_* not in libsmile_torch");

        AttentionBackend previousBackend = AttentionBackends.current();
        AttentionBackends.install(AttentionBackend.FLASHINFER);
        assumeTrue(AttentionBackends.current() == AttentionBackend.FLASHINFER,
                "FlashInfer not compiled into libsmile_torch");
        try {
            // Same shape as QwenVerifyGraphFullModelCaptureTest (which passed on a
            // single GPU): 4 hybrid layers (3 linear-attention + 1 full-attention),
            // real production vocab size. numHeads/numKvHeads/intermediateSize/
            // linearNum{Key,Value}Heads are all divisible by TP_SIZE, as
            // TensorShardSpec.forRank requires.
            String[] types = QwenModelArgs.defaultLayerTypes(4, 4);
            QwenModelArgs args = new QwenModelArgs(
                    64,     // dim
                    4,      // numLayers
                    4,      // numHeads
                    2,      // numKvHeads
                    16,     // headDim
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

            ParallelConfig parallel = ParallelConfig.tensorParallel((byte) 0, (byte) 1);
            TensorParallelGroup tpGroup = new TensorParallelGroup(parallel);

            QwenModel[] models = new QwenModel[TP_SIZE];
            for (int rank = 0; rank < TP_SIZE; rank++) {
                Device device = Device.CUDA(parallel.devices()[rank]);
                TensorShardSpec shard = TensorShardSpec.forRank(
                        TP_SIZE, rank, args.numHeads(), args.numKvHeads(), args.intermediateSize(),
                        args.linearNumKeyHeads(), args.linearNumValueHeads());

                DeltaNetStatePool statePool = new DeltaNetStatePool(
                        args.numLinearAttentionLayers(), shard.linearNumValueHeads(),
                        args.linearKeyHeadDim(), args.linearValueHeadDim(),
                        args.linearConvDim(shard), args.linearConvKernelDim(),
                        Math.max(2, args.maxBatchSize()), device, ScalarType.Float);

                QwenModel model = new QwenModel(args, statePool, shard, tpGroup, null);
                model.to(device);
                model.eval();
                model.setKvCachePool(kvCachePoolWithRealisticPageSize(args.kvCacheLayout(shard), device), false);
                models[rank] = model;
            }

            Qwen qwen = new Qwen("tp2-verify-graph-capture", models, tpGroup, tinyTokenizer(), args);

            int[] prompt = pageAlignedPrompt();
            int requestId = qwen.bind(prompt, 32);
            try (Tensor prefill = qwen.prefillChunk(requestId, prompt, 0, prompt.length)) {
                assertNotNull(prefill);
            }

            int startPos = prompt.length;
            int[] window = {7, 11, 13};

            // Same mechanism as the single-GPU test: repeating the identical
            // (startPos, window) drives 2 eager warmup rounds, one real capture
            // (now involving a real cross-device NCCL all-reduce inside the
            // captured region on each rank), then real replay on every round
            // after — each round independently diffed against a sequential-decode
            // reference computed outside any graph.
            for (int round = 0; round < ROUNDS; round++) {
                // Deliberately no exact-argmax assertion (see
                // QwenVerifyGraphFullModelCaptureTest's identical comment): with
                // random, untrained weights and a ~250k-entry vocab, top logits
                // are frequently near-tied, so a legitimate, tiny floating-point
                // difference between kernels can flip an argmax that is
                // essentially a coin flip. The logit-magnitude diff below is the
                // real, robust correctness signal.
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
