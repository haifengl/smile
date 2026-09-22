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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Directly tests the causal-independence claim behind the MTP verify-window
 * checkpoint-replay fix: {@link GatedDeltaNet}'s per-position verify loop
 * ({@code statePool.verifyWindowActive() == true}) must retain, at every
 * window position {@code r}, DeltaNet recurrent/conv state identical to a
 * genuinely truncated {@code S=r+1} forward run fresh from the same initial
 * state — not merely "old batched call == new looped call" on the whole
 * window, which would not rule out a shared bug in both.
 *
 * <p>Runs on CUDA when available (exercising the real fused native kernels
 * the production path uses) and falls back to the Java reference
 * implementation on CPU otherwise (still exercises the Java-level checkpoint
 * bookkeeping and the loop-vs-truncated equivalence, just not the exact
 * native-kernel numerics) — see {@code GatedDeltaRule}'s native-with-Java-
 * fallback pattern.
 *
 * @author Haifeng Li
 */
public class GatedDeltaNetVerifyLoopEquivalenceTest {

    /**
     * Loop (fused per-step decode conv/recurrent kernels, one call per
     * position) vs. a truncated {@code S>1} reference (batched conv path) —
     * different kernel call granularity for the same math. Bounded per the
     * plan's flagged fp32-conv-upcast note; tight enough to catch a real
     * causal-independence violation.
     */
    private static final double TOLERANCE = 1e-4;

    private static Device testDevice() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0 ? Device.CUDA() : Device.CPU();
    }

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

    private static GatedDeltaNet newDelta(QwenModelArgs args, DeltaNetStatePool pool, Device device) {
        GatedDeltaNet delta = new GatedDeltaNet(args, 0, pool);
        if (device.isCUDA()) {
            // GatedDeltaNet is a plain class (no Layer/LayerBlock supertype), so
            // it has no .to(Device) of its own — move its native module tree
            // directly, the same native call Layer.to(Device)'s default
            // implementation uses (mirrors GatedDeltaNetCudaGraphTest).
            MemorySegment deviceHandle = device.toNative();
            try {
                smile.torch.smile_torch_h.smile_module_to_device(delta.module(), deviceHandle, 1);
            } finally {
                smile.torch.smile_torch_h.smile_device_free(deviceHandle);
            }
        }
        return delta;
    }

    @Test
    public void testGivenVerifyWindowLoopWhenPartialAcceptThenMatchesTruncatedForward() {
        Device device = testDevice();
        int windowLen = 4; // 1 "lastToken" position + 3 draft positions

        String[] types = {QwenModelArgs.LINEAR_ATTENTION};
        QwenModelArgs args = new QwenModelArgs(
                64, 1, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 8, 8, 2, 4, types, 1, 32);
        int hidden = args.dim();

        DeltaNetStatePool pool = new DeltaNetStatePool(
                args.numLinearAttentionLayers(), args.linearNumValueHeads(),
                args.linearKeyHeadDim(), args.linearValueHeadDim(),
                args.linearConvDim(), args.linearConvKernelDim(),
                2, device, ScalarType.Float);
        GatedDeltaNet delta = newDelta(args, pool, device);

        pool.bindRequest(1);
        pool.activateStep(1);
        try {
            // Warm up with a couple of decode steps so recurrent/conv state is
            // non-trivial (not all-zero) before the window under test.
            try (Tensor warm = Tensor.randn(1, 1, hidden).to(device, ScalarType.Float)) {
                for (int i = 0; i < 2; i++) {
                    try (Tensor discard = delta.forward(warm)) {
                        // only the state advance matters
                    }
                }
            }

            // Slot 0 = shared initial state every variant below restores from;
            // slots 1..windowLen = per-position checkpoints the loop retains.
            pool.ensureSpeculativeCheckpoints(windowLen + 1);
            pool.saveCheckpoint(0);

            Tensor[] loopRecurrent = new Tensor[windowLen];
            Tensor[] loopConv = new Tensor[windowLen];
            try (Tensor window = Tensor.randn(1, windowLen, hidden).to(device, ScalarType.Float)) {
                // (b) New per-position loop with retention.
                pool.setVerifyWindowActive(true);
                try (Tensor loopOut = delta.forward(window)) {
                    // only the retained checkpoints matter, not this logits-shaped output
                } finally {
                    pool.setVerifyWindowActive(false);
                }

                // Snapshot every retained per-position checkpoint before the
                // truncated-forward comparisons below start overwriting the
                // active rows via restoreCheckpoint(0).
                for (int r = 0; r < windowLen; r++) {
                    pool.restoreCheckpoint(r + 1);
                    loopRecurrent[r] = pool.recurrent(0).copy();
                    loopRecurrent[r].detachFromScopes();
                    Tensor conv = pool.conv(0);
                    if (conv != null) {
                        loopConv[r] = conv.copy();
                        loopConv[r].detachFromScopes();
                    }
                }

                for (int r = 0; r < windowLen; r++) {
                    // (c) Genuinely truncated S=r+1 forward from the identical initial state.
                    pool.restoreCheckpoint(0);
                    try (var span = Index.slice(0, r + 1);
                         Tensor truncated = window.get(Index.Colon, span, Index.Colon)) {
                        try (Tensor truncatedOut = delta.forward(truncated)) {
                            // only the resulting state matters
                        }
                    }
                    double recurrentDiff = maxAbsDiff(loopRecurrent[r], pool.recurrent(0));
                    assertTrue(recurrentDiff <= TOLERANCE,
                            "window position " + r + ": loop-checkpoint vs truncated-forward "
                                    + "recurrent state maxAbsDiff=" + recurrentDiff
                                    + " exceeds tolerance=" + TOLERANCE);
                    if (loopConv[r] != null) {
                        double convDiff = maxAbsDiff(loopConv[r], pool.conv(0));
                        assertTrue(convDiff <= TOLERANCE,
                                "window position " + r + ": loop-checkpoint vs truncated-forward "
                                        + "conv state maxAbsDiff=" + convDiff
                                        + " exceeds tolerance=" + TOLERANCE);
                    }
                }
            } finally {
                for (Tensor t : loopRecurrent) {
                    if (t != null) {
                        t.close();
                    }
                }
                for (Tensor t : loopConv) {
                    if (t != null) {
                        t.close();
                    }
                }
            }
        } finally {
            pool.releaseSpeculativeCheckpoints();
            pool.unbindRequest(1);
        }
    }
}
