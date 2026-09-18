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
 * <p>Enable buffer/kernel wiring with environment variable
 * {@code SMILE_VERIFY_CUDA_GRAPH=1}. Actual graph <em>capture</em> is a
 * separate, additional gate ({@link #captureEnabled()}) — Stage 4 of the
 * verify-CUDA-graph plan wires the new stable-buffer / graph-capturable-kernel
 * path in shadow-run mode only (every call still runs eager, through the new
 * kernel, but {@code beginCapture} is never reached); Stage 5 flips
 * {@link #captureEnabled()} once Stage 4's numerics have been validated
 * against the existing eager path on real hardware.
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
     * Stage 4: capture is force-disabled regardless of {@link #ENABLED}. Only
     * the buffer/kernel plumbing runs (shadow-run mode); {@code beginCapture}
     * must never be called while this is {@code false}. Stage 5 flips this to
     * a real gate once Stage 4's shadow-run numerics are validated.
     */
    private static final boolean CAPTURE_ENABLED = false;
    /** Set after a capture failure so we stop retrying every few verify steps. */
    private static volatile boolean captureDisabled;

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
     * Returns whether real graph capture may be attempted. {@code false}
     * during Stage 4 shadow-run; {@link #enabled} may still be {@code true}
     * so the new buffer/kernel path runs eagerly.
     *
     * @return {@code true} once Stage 5 enables real capture.
     */
    public static boolean captureEnabled() {
        return CAPTURE_ENABLED && !captureDisabled;
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
}
