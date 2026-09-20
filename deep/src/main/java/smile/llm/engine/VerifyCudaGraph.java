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

import smile.torch.Native;

/**
 * Optional CUDA graph capture for the multi-token MTP window-verify forward
 * ({@code S = numDrafts + 1}), independent of {@link DecodeCudaGraph}.
 *
 * <p>Enable with environment variable {@code SMILE_VERIFY_CUDA_GRAPH=1} —
 * this both wires the new stable-buffer / graph-capturable-kernel path and
 * enables real capture ({@link #captureEnabled()}), mirroring
 * {@code SMILE_DECODE_CUDA_GRAPH}'s single-flag design. Stage 4 of the
 * verify-CUDA-graph plan validated the buffer/kernel path in a shadow-run
 * mode with capture hard-disabled (a now-removed compile-time constant, not a
 * runtime flag); Stage 5 enables real capture once Stage 4's numerics were
 * validated against the existing eager path on real hardware (byte-identical
 * output, no perf regression).
 *
 * <p>Bucketed by {@code (batch, windowLen, numPages)}, mirroring
 * {@link DecodeCudaGraph}'s {@code (batch, numPages)} bucketing plus the
 * extra {@code windowLen} dimension (query length is not implicitly 1 here).
 *
 * @author Haifeng Li
 */
public final class VerifyCudaGraph {
    private static final boolean ENABLED = "1".equals(System.getenv("SMILE_VERIFY_CUDA_GRAPH"));
    private static final boolean AVAILABLE = Native.cudaGraphAvailable();
    /** Set after a capture failure so we stop retrying every few verify steps. */
    private static volatile boolean captureDisabled;
    /**
     * Process-wide lock serializing the {@code beginCapture}/forward/{@code endCapture}
     * region across TP ranks (each rank runs on its own thread, targeting its own
     * device). Real-hardware testing found that letting all ranks capture
     * concurrently — which happens routinely here because every rank reaches the
     * same {@code (batch, windowLen, numPages)} bucket transition on the same
     * round, unlike decode's own graph where per-rank timing drift makes
     * simultaneous capture unlikely — produces a captured graph whose replay
     * reads back a <em>different rank's</em> device/tensor (observed as a clean
     * pairwise device swap across 4 ranks, e.g. rank 0's replay returning rank
     * 3's GPU). Capture happens once per bucket, so serializing only this region
     * (never replay, never the eager path) costs nothing in steady state.
     */
    private static final Object CAPTURE_LOCK = new Object();

    private VerifyCudaGraph() {}

    /**
     * Returns whether verify CUDA-graph buffer/kernel wiring is enabled for
     * this process (independent of whether capture itself is enabled).
     *
     * @return {@code true} when env is set, native API is linked, and capture is not disabled.
     */
    public static boolean enabled() {
        return ENABLED && AVAILABLE && !captureDisabled;
    }

    /**
     * Returns whether real graph capture may be attempted (Stage 5: same gate
     * as {@link #enabled()} — a single flag, mirroring {@code SMILE_DECODE_CUDA_GRAPH}).
     *
     * @return {@code true} when verify CUDA graphs are enabled and capture has
     *         not been disabled after a prior failure.
     */
    public static boolean captureEnabled() {
        return enabled();
    }

    /**
     * Returns whether this verify step is eligible for CUDA graph capture/replay.
     *
     * @param startPositions per-row KV window start positions.
     * @return {@code true} when every row shares the same start position
     *         (a homogeneous cohort; batch &gt; 1 verify-graph is otherwise
     *         out of scope today, but the check does not hardcode batch == 1).
     */
    public static boolean canGraphVerify(int[] startPositions) {
        if (!enabled() || startPositions == null || startPositions.length == 0) {
            return false;
        }
        int pos = startPositions[0];
        for (int i = 1; i < startPositions.length; i++) {
            if (startPositions[i] != pos) {
                return false;
            }
        }
        return true;
    }

    /**
     * Permanently disable verify CUDA graphs for this process (after a capture failure).
     *
     * @param reason human-readable failure reason (for callers; not logged here).
     */
    public static void disableCapture(String reason) {
        if (!captureDisabled) {
            captureDisabled = true;
        }
    }

    /**
     * Returns the number of eager warmup verify steps before graph capture.
     *
     * @return warmup steps per {@code (batch, windowLen, numPages)} bucket.
     */
    public static int warmupSteps() {
        return 2;
    }

    /**
     * Lock guarding the capture region (see {@link #CAPTURE_LOCK}'s javadoc).
     * Callers must hold this for the entire {@code beginCapture}/forward/
     * {@code endCapture} sequence, not just the native calls.
     *
     * @return the process-wide capture-serialization lock.
     */
    public static Object captureLock() {
        return CAPTURE_LOCK;
    }
}
