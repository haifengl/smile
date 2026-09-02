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
 * Phase 2c: optional CUDA graph capture for batch-1 decode forward.
 *
 * <p>Unlike {@code SMILE_FLASHINFER_DECODE_CUDA_GRAPH} (reverted — FlashInfer plan
 * flag only), this captures the full decode forward via {@code at::cuda::CUDAGraph}.
 *
 * <p>Enable with environment variable {@code SMILE_DECODE_CUDA_GRAPH=1}.
 *
 * @author Haifeng Li
 */
public final class DecodeCudaGraph {
    private static final boolean ENABLED = "1".equals(System.getenv("SMILE_DECODE_CUDA_GRAPH"));
    private static final boolean AVAILABLE = Native.cudaGraphAvailable();
    /** Set after a capture failure so we stop retrying every few decode steps. */
    private static volatile boolean captureDisabled;

    private DecodeCudaGraph() {}

    /** @return {@code true} when env is set, native API is linked, and capture is not disabled. */
    public static boolean enabled() {
        return ENABLED && AVAILABLE && !captureDisabled;
    }

    /** Permanently disable decode CUDA graphs for this process (after capture failure). */
    public static void disableCapture(String reason) {
        if (!captureDisabled) {
            captureDisabled = true;
            // Logged by caller; keep process-wide so all TP ranks stop retrying.
        }
    }

    /** Number of eager warmup decode steps before graph capture per KV-page bucket. */
    public static int warmupSteps() {
        return 2;
    }

    private static final ThreadLocal<Boolean> PERSISTENT_LOGITS =
            ThreadLocal.withInitial(() -> false);

    /**
     * Marks that the current decode step returned logits backed by a captured CUDA
     * graph output buffer (must not be closed by the caller).
     */
    public static void markPersistentLogits(boolean persistent) {
        PERSISTENT_LOGITS.set(persistent);
    }

    /** @return {@code true} when decode logits outlive the logits-row copy step. */
    public static boolean persistentLogits() {
        return PERSISTENT_LOGITS.get();
    }
}
