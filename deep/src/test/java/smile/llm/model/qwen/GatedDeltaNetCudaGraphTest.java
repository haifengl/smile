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

import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Isolates whether {@link GatedDeltaNet#forward} for a multi-token window
 * (S &gt; 1, the shape used by MTP window verify) is safe under
 * {@code at::cuda::CUDAGraph} capture/replay, independent of the full model
 * forward, tensor-parallel multi-rank dispatch, and the FlashInfer attention
 * kernel — all of which are separately validated elsewhere
 * ({@code VerifyCudaGraphStage1KernelTest}, {@code VerifyCudaGraphStage2CaptureReplayTest}).
 *
 * <p>Real-hardware Stage 5 testing of the full model forward under capture
 * produced corrupted tensor metadata on replay (wrong device, wrong shape,
 * sometimes a native SIGSEGV reading the tensor's own metadata) that survived
 * serializing capture across TP ranks — ruling out concurrent multi-rank
 * capture as the cause. DeltaNet's {@code S > 1} path was flagged by an
 * earlier static-inspection-only audit as "new to capture, not
 * execution-tested" (unlike its {@code S == 1} decode path, which the
 * already-working {@code SMILE_DECODE_CUDA_GRAPH} graph proves safe). This
 * test executes that specific path under real capture/replay to get a
 * decisive answer.
 *
 * <p>Methodology mirrors {@code Qwen.saveDeltaNetCheckpoint}/
 * {@code restoreDeltaNetCheckpoint}'s real production usage: at each step,
 * checkpoint the pre-step state, run the captured/replayed path (advancing
 * state for real, exactly like a live verify round), checkpoint that
 * resulting state, rewind to the pre-step checkpoint, run a fresh eager
 * reference forward on the identical input, diff the two outputs, then
 * restore the captured path's own post-step state so the next iteration
 * continues its real trajectory (not the eager reference's) — matching how
 * replay only ever advances the one live state in production.
 *
 * @author Haifeng Li
 */
public class GatedDeltaNetCudaGraphTest {

    private static final double TOLERANCE = 5e-2;
    private static final int REPLAYS = 20;

    private static boolean cudaAvailable() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0;
    }

    /**
     * Max absolute difference via Java arrays rather than {@code Tensor.max()}
     * + {@code doubleValue()} — {@code smile_tensor_max} is not a true global
     * reduction in the current native binary, and the native {@code item_*}
     * call behind {@code doubleValue()} aborts the JVM (SIGABRT) on a
     * non-scalar result.
     */
    private static double maxAbsDiff(Tensor a, Tensor b) {
        try (Tensor diff = a.to(ScalarType.Float).sub(b.to(ScalarType.Float)).abs();
             Tensor cpu = diff.to(Device.CPU())) {
            double max = 0.0;
            for (float v : cpu.floatArray()) {
                double av = Math.abs((double) v);
                if (av > max) {
                    max = av;
                }
            }
            return max;
        }
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
    public void testGivenCapturedGraphWhenReplayedThenMatchesEagerAcrossManySteps() {
        assumeTrue(cudaAvailable(), "CUDA not available in this environment");
        assumeTrue(Native.cudaGraphAvailable(), "smile_cuda_graph_* not in libsmile_torch");

        Device device = Device.CUDA();
        int windowLen = 3;

        String[] types = {QwenModelArgs.LINEAR_ATTENTION};
        QwenModelArgs args = new QwenModelArgs(
                64,   // dim
                1,    // numLayers
                4,    // numHeads
                2,    // numKvHeads
                16,   // headDim
                100,  // vocabSize
                128,  // intermediateSize
                1e-6, // normEps
                10000.0, // ropeTheta
                0.25, // partialRotaryFactor
                4,    // linearConvKernelDim
                8,    // linearKeyHeadDim
                8,    // linearValueHeadDim
                2,    // linearNumKeyHeads
                4,    // linearNumValueHeads
                types,
                1,    // maxBatchSize
                32    // maxSeqLen
        );
        int hidden = args.dim();

        DeltaNetStatePool pool = new DeltaNetStatePool(
                args.numLinearAttentionLayers(), args.linearNumValueHeads(),
                args.linearKeyHeadDim(), args.linearValueHeadDim(),
                args.linearConvDim(), args.linearConvKernelDim(),
                Math.max(2, args.maxBatchSize()), device, ScalarType.Float);
        GatedDeltaNet delta = new GatedDeltaNet(args, 0, pool);
        delta.to(device);

        pool.bindRequest(1);
        pool.activateStep(1);

        try (Tensor h = Tensor.randn(1, windowLen, hidden).to(device, ScalarType.Float)) {
            // activateStep is called once, here, for the whole test — not per
            // iteration. It packs the "home row" into the working slot; since
            // production keeps the home row in sync via scatterActive() at
            // round-end (never called here) and this test only ever binds a
            // single request, re-activating mid-loop would silently overwrite
            // whatever checkpoint state was just restored with the stale,
            // never-updated home row.
            for (int i = 0; i < 2; i++) {
                try (Tensor warm = delta.forward(h)) {
                    // discard output; only the state advance matters here
                }
            }

            pool.ensureSpeculativeCheckpoints(2);

            MemorySegment graph = Native.cudaGraphCreate();
            assumeTrue(graph != null && graph.address() != 0,
                    "smile_cuda_graph_create returned null");
            Tensor capturedOut = null;
            try {
                for (int i = 0; i < REPLAYS; i++) {
                    refill(h, device, ScalarType.Float, 1, windowLen, hidden);
                    pool.saveCheckpoint(0); // pre-step state

                    if (i == 0) {
                        Native.cudaGraphCaptureBegin(graph, 0);
                        capturedOut = delta.forward(h);
                        Native.cudaGraphCaptureEnd(graph);
                        assertTrue(Native.cudaGraphIsReady(graph),
                                "graph not ready after capture_end");
                    } else {
                        Native.cudaGraphReplay(graph);
                    }

                    pool.saveCheckpoint(1); // post-captured-path state

                    pool.restoreCheckpoint(0); // rewind to pre-step
                    try (Tensor eager = delta.forward(h)) {
                        double d = maxAbsDiff(capturedOut, eager);
                        assertTrue(d < TOLERANCE,
                                "step " + i + " (capture=" + (i == 0) + ") captured vs fresh "
                                        + "eager max abs diff=" + d
                                        + " — captured/replayed DeltaNet output diverged from "
                                        + "an eager forward on the identical input/state");
                    }

                    pool.restoreCheckpoint(1); // continue the captured path's own trajectory
                }
            } finally {
                if (capturedOut != null) {
                    capturedOut.close();
                }
                Native.cudaGraphDestroy(graph);
            }
        } finally {
            pool.releaseSpeculativeCheckpoints();
            pool.unbindRequest(1);
        }
    }
}
