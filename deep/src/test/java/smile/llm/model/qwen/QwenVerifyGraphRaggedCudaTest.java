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
 * Ragged batched verify CUDA graph regression test.
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
public class QwenVerifyGraphRaggedCudaTest {

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

    private static int[] prompt(int seed, int len) {
        int[] p = new int[len];
        for (int i = 0; i < len; i++) {
            p[i] = 1 + ((i * 3 + seed) % 50);
        }
        return p;
    }

    @Test
    public void testGivenRaggedCohortWhenVerifyGraphReplayedThenBitExactWithEagerAndCapturedOncePerBatchSize() {
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
                    4, 16, 16, 2, 4, types, 4, 256, 1, 2);

            DeltaNetStatePool statePool = new DeltaNetStatePool(
                    args.numLinearAttentionLayers(), args.linearNumValueHeads(),
                    args.linearKeyHeadDim(), args.linearValueHeadDim(),
                    args.linearConvDim(), args.linearConvKernelDim(),
                    args.maxBatchSize(), device, ScalarType.Float);
            QwenModel model = new QwenModel(args, statePool);
            model.to(device, ScalarType.BFloat16);
            model.eval();
            KvCacheLayout layout = args.kvCacheLayout();
            model.setKvCachePool(new KvCachePool(layout.numLayers(),
                    layout.maxBatchSize() * layout.maxSeqLen(), layout.numKvHeads(), layout.headDim(),
                    PAGE_SIZE, device, ScalarType.BFloat16), false);

            Qwen qwen = new Qwen("cuda-verify-graph-ragged", model, tinyTokenizer(), args);
            int[] lens = {8, 21, 35};
            int n = lens.length;
            int[] ids = new int[n];
            int[] pos = new int[n];
            for (int i = 0; i < n; i++) {
                int[] p = prompt(i * 5, lens[i]);
                ids[i] = qwen.bind(p, 200);
                try (Tensor prefill = qwen.prefillChunk(ids[i], p, 0, p.length)) {
                    assertTrue(prefill != null);
                }
                pos[i] = lens[i];
            }

            int capturesBefore = smile.llm.engine.VerifyCudaGraphSession.CAPTURES.get();
            for (int round = 0; round < ROUNDS; round++) {
                // Rounds 14..24 run a 2-row cohort (rows 0 and 2) to exercise a batch-size change.
                int[] rows = round >= 14 && round < 25 ? new int[]{0, 2} : new int[]{0, 1, 2};
                int b = rows.length;
                int[] cohort = new int[b];
                int[] start = new int[b];
                for (int j = 0; j < b; j++) {
                    cohort[j] = ids[rows[j]];
                    start[j] = pos[rows[j]];
                }
                model.kvCachePool().activateStep(cohort);
                statePool.activateStep(cohort);
                statePool.ensureSpeculativeCheckpoints(WINDOW + 2);
                long[] toks = new long[b * WINDOW];
                for (int j = 0; j < b; j++) {
                    for (int i = 0; i < WINDOW; i++) {
                        toks[j * WINDOW + i] = 1 + ((round * 7 + rows[j] * 11 + i * 3) % 200);
                    }
                }
                try (Tensor tokens = Tensor.of(toks).reshape(b, WINDOW).to(device)) {
                    statePool.saveCheckpoint(0);
                    statePool.setVerifyWindowActive(true);
                    float[] graphLogits;
                    Tensor g = model.forwardVerifyGraph(tokens, start);
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
                    Tensor[] graphHidden = new Tensor[b];
                    for (int j = 0; j < b; j++) {
                        graphHidden[j] = model.copyWindowHiddenRows(j, 0, WINDOW);
                        assertTrue(graphHidden[j] != null, "round " + round + " row " + j + ": no window hidden");
                    }
                    statePool.restoreCheckpoint(0);
                    statePool.setVerifyWindowActive(true);
                    float[] eagerLogits;
                    try (Tensor e = model.forwardVerifyGraphCodeEager(tokens, start);
                         Tensor eCpu = e.to(Device.CPU())) {
                        eagerLogits = eCpu.floatArray();
                    } finally {
                        statePool.setVerifyWindowActive(false);
                    }
                    for (int j = 0; j < b; j++) {
                        try (Tensor eagerHidden = model.copyWindowHiddenRows(j, 0, WINDOW);
                             Tensor diff = graphHidden[j].to(ScalarType.Float)
                                     .sub(eagerHidden.to(ScalarType.Float)).abs();
                             Tensor dCpu = diff.to(Device.CPU())) {
                            float hMax = 0f;
                            for (float v : dCpu.floatArray()) {
                                hMax = Math.max(hMax, v);
                            }
                            assertTrue(hMax == 0f, "round " + round + " row " + j + " start=" + start[j]
                                    + ": graph window hidden differs from eager (maxAbs=" + hMax + ")");
                        } finally {
                            graphHidden[j].close();
                        }
                    }
                    assertEquals(eagerLogits.length, graphLogits.length);
                    float maxAbs = 0f;
                    for (int i = 0; i < eagerLogits.length; i++) {
                        maxAbs = Math.max(maxAbs, Math.abs(eagerLogits[i] - graphLogits[i]));
                    }
                    assertTrue(maxAbs == 0f, "round " + round + " batch=" + b + " starts="
                            + java.util.Arrays.toString(start) + ": graph replay vs same-kernel eager maxAbs=" + maxAbs);
                }
                statePool.scatterActive();
                for (int j = 0; j < b; j++) {
                    pos[rows[j]] += WINDOW;
                }
            }
            // Batch sizes visited: 3, then 2, then 3 again => one capture per contiguous run.
            assertEquals(3, smile.llm.engine.VerifyCudaGraphSession.CAPTURES.get() - capturesBefore,
                    "expected one capture per batch-size change (3 -> 2 -> 3)");
            for (int id : ids) {
                qwen.evict(id);
            }
        } finally {
            AttentionBackends.install(previousBackend);
        }
    }
}
