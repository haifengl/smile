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
import smile.llm.engine.VerifyCudaGraph;
import smile.util.Bytes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Multi-round, multi-page-boundary regression test for the verify CUDA graph.
 *
 * <p>Every other CUDA graph test is single-bucket (or repeats one fixed
 * {@code startPos}), which is why a graph replayed with a stale plan or a moved
 * CSR across KV page boundaries went unnoticed. This one advances the sequence
 * by a full window per round (page size 4 and window 3 ⇒ a boundary almost
 * every round) and, each round, compares the graph path's logits against the
 * exact same captured code run eagerly with no graph
 * ({@link QwenModel#forwardVerifyGraphCodeEager}). Replay must be bit-exact
 * with eager, and the whole run must need exactly one graph capture.
 *
 * @author Haifeng Li
 */
@Tag("cuda")
public class QwenVerifyGraphMultiBucketCudaTest {

    private static final int PAGE_SIZE = 4;
    private static final int WINDOW = 3;
    private static final int ROUNDS = 40;

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

    @Test
    public void testGivenManyRoundsAcrossPageBoundariesWhenVerifyGraphReplayedThenBitExactWithEagerAndCapturedOnce() {
        assumeTrue(cudaAvailable(), "CUDA not available in this environment");
        assumeTrue(smile.torch.Native.cudaGraphAvailable(), "smile_cuda_graph_* not in libsmile_torch");
        assumeTrue(VerifyCudaGraph.enabled(), "SMILE_VERIFY_CUDA_GRAPH not enabled");

        AttentionBackend previousBackend = AttentionBackends.current();
        AttentionBackends.install(AttentionBackend.FLASHINFER);
        assumeTrue(AttentionBackends.current() == AttentionBackend.FLASHINFER,
                "FlashInfer not compiled into libsmile_torch");
        try {
            Device device = Device.CUDA();
            String[] types = QwenModelArgs.defaultLayerTypes(4, 4);
            QwenModelArgs args = new QwenModelArgs(
                    64, 4, 4, 2, 64, 1024, 128, 1e-6, 10000.0, 0.25,
                    4, 16, 16, 2, 4, types, 2, 256, 1, 2);

            DeltaNetStatePool statePool = new DeltaNetStatePool(
                    args.numLinearAttentionLayers(), args.linearNumValueHeads(),
                    args.linearKeyHeadDim(), args.linearValueHeadDim(),
                    args.linearConvDim(), args.linearConvKernelDim(),
                    Math.max(2, args.maxBatchSize()), device, ScalarType.Float);
            QwenModel model = new QwenModel(args, statePool);
            model.to(device, ScalarType.BFloat16);
            model.eval();
            KvCacheLayout layout = args.kvCacheLayout();
            model.setKvCachePool(new KvCachePool(layout.numLayers(),
                    layout.maxBatchSize() * layout.maxSeqLen(), layout.numKvHeads(), layout.headDim(),
                    PAGE_SIZE, device, ScalarType.BFloat16), false);

            Qwen qwen = new Qwen("cuda-verify-graph-multibucket", model, tinyTokenizer(), args);
            int[] prompt = new int[8];
            for (int i = 0; i < prompt.length; i++) {
                prompt[i] = 1 + (i % 50);
            }
            int requestId = qwen.bind(prompt, 200);
            try (Tensor prefill = qwen.prefillChunk(requestId, prompt, 0, prompt.length)) {
                assertTrue(prefill != null);
            }
            model.kvCachePool().activateStep(requestId);
            statePool.activateStep(requestId);
            // slot 0 = pre-round state, slots 1..WINDOW = per-position, +1 scratch.
            statePool.ensureSpeculativeCheckpoints(WINDOW + 2);

            int[] prompt2 = new int[37];
            for (int i = 0; i < prompt2.length; i++) {
                prompt2[i] = 2 + (i % 40);
            }
            int capturesBefore = smile.llm.engine.VerifyCudaGraphSession.CAPTURES.get();
            int startPos = prompt.length;
            for (int round = 0; round < ROUNDS; round++) {
                if (round == ROUNDS / 3) {
                    // An ordinary prefill of a different shape between graph rounds: it
                    // reallocates the generic window-hidden buffer. The captured graph must be
                    // unaffected (it owns a separate stable hidden buffer).
                    statePool.scatterActive();
                    int req2 = qwen.bind(prompt2, 100);
                    try (Tensor p2 = qwen.prefillChunk(req2, prompt2, 0, prompt2.length)) {
                        assertTrue(p2 != null);
                    }
                    qwen.evict(req2);
                    model.kvCachePool().activateStep(requestId);
                    statePool.activateStep(requestId);
                }
                long[] toks = new long[WINDOW];
                for (int i = 0; i < WINDOW; i++) {
                    toks[i] = 1 + ((round * 7 + i * 3) % 200);
                }
                try (Tensor tokens = Tensor.of(toks).reshape(1, WINDOW).to(device)) {
                    statePool.saveCheckpoint(0);
                    statePool.setVerifyWindowActive(true);
                    float[] graphLogits;
                    // The graph path returns its persistent logits buffer (replay writes into
                    // it again next round): the caller must not close it. Mirrors
                    // Qwen.ownedVerifyWindowLogits.
                    Tensor g = model.forwardVerifyGraph(tokens, startPos);
                    boolean persistent = VerifyCudaGraph.persistentLogits();
                    try (Tensor gCpu = g.to(Device.CPU())) {
                        graphLogits = gCpu.floatArray();
                    } finally {
                        if (!persistent) {
                            g.close();
                        }
                        VerifyCudaGraph.markPersistentLogits(false);
                        statePool.setVerifyWindowActive(false);
                    }
                    // Window-hidden rows the graph run produced (read BEFORE the eager reference
                    // below rewrites the same buffer): feeds the MTP draft head's history.
                    Tensor graphHidden = model.copyWindowHiddenRows(0, WINDOW);
                    assertTrue(graphHidden != null, "round " + round + ": graph run must expose its window hidden (fromGraph=" + model.windowHiddenFromGraph + ", graphBuf=" + (model.verifyGraphHiddenBuf != null) + ", genericBuf=" + (model.verifyWindowNormalizedBuf != null) + ")");
                    // Same code, eagerly, from the identical pre-round DeltaNet state.
                    statePool.restoreCheckpoint(0);
                    statePool.setVerifyWindowActive(true);
                    float[] eagerLogits;
                    try (Tensor e = model.forwardVerifyGraphCodeEager(tokens, startPos);
                         Tensor eCpu = e.to(Device.CPU())) {
                        eagerLogits = eCpu.floatArray();
                    } finally {
                        statePool.setVerifyWindowActive(false);
                    }
                    try (Tensor eagerHidden = model.copyWindowHiddenRows(0, WINDOW);
                         Tensor diff = graphHidden.to(ScalarType.Float).sub(eagerHidden.to(ScalarType.Float)).abs();
                         Tensor dCpu = diff.to(Device.CPU())) {
                        float hMax = 0f;
                        for (float v : dCpu.floatArray()) {
                            hMax = Math.max(hMax, v);
                        }
                        assertTrue(hMax == 0f, "round " + round + " startPos=" + startPos
                                + ": graph window hidden differs from eager (maxAbs=" + hMax + ")");
                    } finally {
                        graphHidden.close();
                    }
                    assertEquals(eagerLogits.length, graphLogits.length);
                    float maxAbs = 0f;
                    for (int i = 0; i < eagerLogits.length; i++) {
                        maxAbs = Math.max(maxAbs, Math.abs(eagerLogits[i] - graphLogits[i]));
                    }
                    assertTrue(maxAbs == 0f,
                            "round " + round + " startPos=" + startPos
                                    + ": graph replay vs same-kernel eager maxAbs=" + maxAbs);
                }
                startPos += WINDOW; // accept the whole window; state already advanced
            }
            // One session, one bucket (batch, windowLen): a single capture for the whole run.
            assertEquals(1, smile.llm.engine.VerifyCudaGraphSession.CAPTURES.get() - capturesBefore,
                    "expected exactly one graph capture across all page boundaries");
            qwen.evict(requestId);
        } finally {
            AttentionBackends.install(previousBackend);
        }
    }
}
