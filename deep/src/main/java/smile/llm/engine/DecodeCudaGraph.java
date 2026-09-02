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
 * Phase 2c: optional CUDA graph capture for batch-1 decode forward.
 *
 * <p>Unlike {@code SMILE_FLASHINFER_DECODE_CUDA_GRAPH} (reverted — FlashInfer plan
 * flag only), this captures the full decode forward via {@code at::cuda::CUDAGraph}.
 *
 * <p>Enable with environment variable {@code SMILE_DECODE_CUDA_GRAPH=1}.
 *
 * <p><b>Phase 2d (multi-batch):</b> graphs are bucketed by
 * {@code (batch, numPages)} for uniform decode steps: batch must be a power of
 * two in {@code [1, maxBatch]}, and every row must share the same KV cache
 * length. RoPE, token, logits, and KV index buffers are sized to the live batch.
 *
 * @author Haifeng Li
 */
public final class DecodeCudaGraph {
    private static final boolean ENABLED = "1".equals(System.getenv("SMILE_DECODE_CUDA_GRAPH"));
    private static final boolean AVAILABLE = Native.cudaGraphAvailable();
    private static final int MAX_BATCH = parseMaxBatch();
    /** Set after a capture failure so we stop retrying every few decode steps. */
    private static volatile boolean captureDisabled;
    /**
     * Set by TP worker threads when decode returns a graph-owned logits buffer;
     * read by the inference-engine thread after {@code Future.get()} (not ThreadLocal).
     */
    private static final AtomicBoolean PERSISTENT_LOGITS = new AtomicBoolean(false);

    private DecodeCudaGraph() {}

    /** @return {@code true} when env is set, native API is linked, and capture is not disabled. */
    public static boolean enabled() {
        return ENABLED && AVAILABLE && !captureDisabled;
    }

    /** Maximum batch size eligible for decode CUDA graphs (power-of-two buckets). */
    public static int maxBatch() {
        return MAX_BATCH;
    }

    /**
     * @return {@code true} when {@code batch} is a supported graph bucket size.
     */
    public static boolean supportsBatch(int batch) {
        return batch > 0 && batch <= MAX_BATCH && (batch & (batch - 1)) == 0;
    }

    /**
     * @return {@code true} when every row shares the same KV write position and
     *         the batch size is graph-eligible.
     */
    public static boolean canGraphDecode(int[] cachePositions) {
        if (!enabled() || cachePositions == null || cachePositions.length == 0) {
            return false;
        }
        if (!supportsBatch(cachePositions.length)) {
            return false;
        }
        int pos = cachePositions[0];
        for (int i = 1; i < cachePositions.length; i++) {
            if (cachePositions[i] != pos) {
                return false;
            }
        }
        return true;
    }

    private static int parseMaxBatch() {
        String raw = System.getenv("SMILE_DECODE_CUDA_GRAPH_MAX_BATCH");
        if (raw == null || raw.isEmpty()) {
            return 16;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v >= 1 ? v : 16;
        } catch (NumberFormatException e) {
            return 16;
        }
    }

    /** Permanently disable decode CUDA graphs for this process (after capture failure). */
    public static void disableCapture(String reason) {
        if (!captureDisabled) {
            captureDisabled = true;
        }
    }

    /** Number of eager warmup decode steps before graph capture per KV-page bucket. */
    public static int warmupSteps() {
        return 2;
    }

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
