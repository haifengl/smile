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

import java.util.concurrent.atomic.AtomicBoolean;
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
    /**
     * Diagnostic only (never enabled by default, doubles verify cost every
     * round): recompute the same verify window eagerly and log the numeric
     * gap against the graph path's own output, on real traffic. Added to
     * investigate a real-hardware finding that verify-graph measurably
     * lowers MTP acceptance vs. the identical eager path on the exact same
     * prompt/model, despite passing isolated correctness tests at a looser
     * tolerance (small random-weight models, not the real trained model
     * where acceptance is an exact-match test sensitive to any systematic
     * numeric offset between two independently-implemented kernels).
     */
    private static final boolean DEBUG_DIFF =
            "1".equals(System.getenv("SMILE_VERIFY_CUDA_GRAPH_DEBUG_DIFF"));
    /** Set after a capture failure so we stop retrying every few verify steps. */
    private static volatile boolean captureDisabled;
    private static final AtomicBoolean PERSISTENT_LOGITS = new AtomicBoolean(false);

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
     * Returns whether the diagnostic eager-vs-graph verify diff is enabled
     * (see {@link #DEBUG_DIFF}).
     *
     * @return {@code true} when {@code SMILE_VERIFY_CUDA_GRAPH_DEBUG_DIFF=1}.
     */
    public static boolean debugDiff() {
        return DEBUG_DIFF;
    }

    /**
     * Marks that the current verify step returned logits backed by a captured
     * CUDA graph output buffer (must not be closed by the caller) — mirrors
     * {@link DecodeCudaGraph#markPersistentLogits(boolean)}. A shared
     * {@link AtomicBoolean}, not thread-local: TP fan-out sets this from each
     * rank's worker thread, and the join point (after every rank's future
     * completes) reads it back on the calling thread.
     *
     * @param persistent {@code true} when logits outlive the caller's own scope.
     */
    public static void markPersistentLogits(boolean persistent) {
        PERSISTENT_LOGITS.set(persistent);
    }

    /**
     * Returns whether verify logits outlive the caller's own scope (i.e. are
     * QwenModel's own reused capture buffer, not a throwaway eager tensor).
     *
     * @return {@code true} when the caller must not close the returned tensor.
     */
    public static boolean persistentLogits() {
        return PERSISTENT_LOGITS.get();
    }
}
