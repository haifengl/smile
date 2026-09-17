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
package smile.llm.engine;

import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.attention.FlashInferWorkspace;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SMILE_VERIFY_CUDA_GRAPH Stage 2 go/no-go: capture the Stage 1 kernel once,
 * then replay it &ge;20 times with different KV content and CSR values written
 * in place between replays (same tensor addresses throughout — mutating
 * addresses would defeat the whole point), diffing every replay against a
 * fresh eager call on the identical (new) inputs.
 *
 * <p>Per the plan, this is the step most likely to produce a <em>silent
 * wrong-answer</em> bug (a replay reading back capture-time/stale data instead
 * of the live inputs) rather than a crash — hence the per-iteration diff
 * against a fresh eager reference, not just "did replay run without erroring."
 *
 * <p><b>Requires {@code SMILE_VERIFY_CUDA_GRAPH=1} in the JVM's process
 * environment</b> (set before the JVM starts — the native check reads
 * {@code getenv} once and caches it). Without it, the kernel never recognizes
 * the stream as capturing and would attempt the eager (possibly
 * capture-unsafe) replanning path during an active {@code cudaStreamCapture}
 * region; this test skips itself rather than risk that.
 *
 * @author Haifeng Li
 */
public class VerifyCudaGraphStage2CaptureReplayTest {

    private static final double TOLERANCE = 5e-2;
    private static final int REPLAYS = 24;

    private static boolean cudaAvailable() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0;
    }

    private static boolean verifyGraphEnvSet() {
        return "1".equals(System.getenv("SMILE_VERIFY_CUDA_GRAPH"));
    }

    /** In-place full-tensor value replacement that preserves the tensor's address. */
    private static void refill(Tensor t, Device device, ScalarType dtype, long... shape) {
        try (Tensor fresh = Tensor.randn(shape).to(device, dtype)) {
            Index[] full = new Index[shape.length];
            for (int i = 0; i < shape.length; i++) {
                full[i] = Index.Colon;
            }
            t.put_(fresh, full);
        }
    }

    @Test
    public void testGivenCapturedGraphWhenReplayedWithVaryingKvThenMatchesEagerEveryTime() {
        assumeTrue(cudaAvailable(), "CUDA not available in this environment");
        assumeTrue(Native.cudaGraphAvailable(), "smile_cuda_graph_* not in libsmile_torch");
        assumeTrue(verifyGraphEnvSet(),
                "SMILE_VERIFY_CUDA_GRAPH=1 must be set in the JVM's environment before it "
                        + "starts (see class javadoc) — skipping rather than risk an eager "
                        + "replan attempt during active stream capture");

        Device device = Device.CUDA();
        int pageSize = 8, numSlots = 8, numQoHeads = 2, numKvHeads = 2, headDim = 64, qoLen = 2;

        try (FlashInferWorkspace ws = FlashInferWorkspace.create(0, 0)) {
            assumeTrue(ws != null, "FlashInfer not compiled into libsmile_torch");
            MemorySegment wsHandle = ws.handle();

            try (Tensor query = Tensor.randn(1, numQoHeads, qoLen, headDim).to(device, ScalarType.BFloat16);
                 Tensor kCache = Tensor.randn(numSlots, numKvHeads, headDim).to(device, ScalarType.BFloat16);
                 Tensor vCache = Tensor.randn(numSlots, numKvHeads, headDim).to(device, ScalarType.BFloat16);
                 Tensor kvIndptr = Tensor.of(new int[]{0, 1}).to(device);
                 Tensor kvIndices = Tensor.of(new int[]{0}).to(device);
                 Tensor kvLastPageLen = Tensor.of(new int[]{pageSize}).to(device);
                 Tensor qoIndptr = Tensor.of(new int[]{0, qoLen}).to(device)) {

                // Warmup: two eager calls on these exact tensor addresses so the native
                // verify-plan cache is populated (and, on the 2nd call, hits the
                // pointer-identical/skip-D2H fast path) before attempting capture —
                // mirrors run_batch_decode's "2 warmup steps before capture" contract.
                for (int i = 0; i < 2; i++) {
                    try (Tensor warm = Native.flashInferAttentionVerifyCapturable(
                            query, kCache, vCache, qoIndptr, kvIndptr, kvIndices, kvLastPageLen,
                            pageSize, numKvHeads, headDim, qoLen, -1.0, 1.0f, 1.0f, wsHandle)) {
                        // discarded; only the plan-cache side effect matters here
                    }
                }

                MemorySegment graph = Native.cudaGraphCreate();
                assumeTrue(graph != null && graph.address() != 0,
                        "smile_cuda_graph_create returned null");
                Tensor capturedOut = null;
                try {
                    Native.cudaGraphCaptureBegin(graph, 0);
                    capturedOut = Native.flashInferAttentionVerifyCapturable(
                            query, kCache, vCache, qoIndptr, kvIndptr, kvIndices, kvLastPageLen,
                            pageSize, numKvHeads, headDim, qoLen, -1.0, 1.0f, 1.0f, wsHandle);
                    Native.cudaGraphCaptureEnd(graph);
                    assertTrue(Native.cudaGraphIsReady(graph), "graph not ready after capture_end");

                    int[] lastPageLens = {3, 8, 4, 6, 5, 7, 8, 3, 4, 5, 6, 7,
                            3, 8, 4, 6, 5, 7, 8, 3, 4, 5, 6, 7};
                    assertTrue(lastPageLens.length >= REPLAYS);
                    for (int i = 0; i < REPLAYS; i++) {
                        // Same addresses every iteration — only values change in place.
                        refill(query, device, ScalarType.BFloat16, 1, numQoHeads, qoLen, headDim);
                        refill(kCache, device, ScalarType.BFloat16, numSlots, numKvHeads, headDim);
                        refill(vCache, device, ScalarType.BFloat16, numSlots, numKvHeads, headDim);
                        kvLastPageLen.fill_(lastPageLens[i]);

                        Native.cudaGraphReplay(graph);

                        try (Tensor eager = Native.flashInferAttentionPagedRaw(
                                query, kCache, vCache, kvIndptr, kvIndices, kvLastPageLen,
                                pageSize, numKvHeads, headDim, lastPageLens[i],
                                -1.0, /*isCausal=*/true, wsHandle);
                             Tensor diff = capturedOut.to(ScalarType.Float)
                                     .sub(eager.to(ScalarType.Float)).abs();
                             Tensor maxDiff = diff.max()) {
                            double d = maxDiff.doubleValue();
                            assertTrue(d < TOLERANCE,
                                    "replay #" + i + " (lastPageLen=" + lastPageLens[i]
                                            + ") vs fresh eager max abs diff=" + d
                                            + " — replay may be reading stale/capture-time data");
                        }
                    }
                } finally {
                    if (capturedOut != null) {
                        capturedOut.close();
                    }
                    Native.cudaGraphDestroy(graph);
                }
            }
        }
    }
}
