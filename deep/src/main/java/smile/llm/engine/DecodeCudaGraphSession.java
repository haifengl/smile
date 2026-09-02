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
import smile.torch.Native;

/**
 * Per-device CUDA graph session for uniform decode (batch 1 or power-of-two B).
 *
 * <p>Graphs are bucketed by {@code (batch, numPages)}. Within a bucket,
 * {@code last_page_len} and per-row KV indices are updated in place before
 * {@link #replay()}.
 *
 * @author Haifeng Li
 */
public final class DecodeCudaGraphSession implements AutoCloseable {
    private MemorySegment handle;
    private int capturedNumPages = -1;
    private int capturedBatch = -1;
    private int warmupRemaining = DecodeCudaGraph.warmupSteps();
    private boolean ready;
    private boolean capturing;
    private long captureBeginNs;
    private long lastCaptureMs;

    /** @return native graph handle, or null when unavailable. */
    public static DecodeCudaGraphSession tryCreate() {
        if (!DecodeCudaGraph.enabled()) {
            return null;
        }
        MemorySegment h = Native.cudaGraphCreate();
        if (h == null || h.address() == 0) {
            return null;
        }
        return new DecodeCudaGraphSession(h);
    }

    private DecodeCudaGraphSession(MemorySegment handle) {
        this.handle = handle;
    }

    /** @return {@code true} when a graph for {@code (batch, numPages)} can be replayed. */
    public boolean canReplay(int batch, int numPages) {
        return ready && !capturing && capturedBatch == batch && capturedNumPages == numPages;
    }

    /**
     * Marks one eager warmup step for the current bucket.
     *
     * @param batch    decode batch size.
     * @param numPages KV page count for this decode step.
     * @param tpRank   tensor-parallel rank for logging ({@code >= 0}).
     * @return {@code true} when the next forward should capture a new graph.
     */
    public boolean shouldCapture(int batch, int numPages, int tpRank) {
        if (ready && capturedBatch == batch && capturedNumPages == numPages) {
            return false;
        }
        if (capturedBatch != batch || capturedNumPages != numPages) {
            resetForNewBucket(batch, numPages);
        }
        if (warmupRemaining > 0) {
            int step = DecodeCudaGraph.warmupSteps() - warmupRemaining + 1;
            DecodeCudaGraphLog.bucketWarmup(tpRank, batch, numPages, step,
                    DecodeCudaGraph.warmupSteps());
            warmupRemaining--;
            return false;
        }
        return true;
    }

    private void resetForNewBucket(int batch, int numPages) {
        ready = false;
        capturing = false;
        capturedBatch = batch;
        capturedNumPages = numPages;
        warmupRemaining = DecodeCudaGraph.warmupSteps();
    }

    /**
     * Begins CUDA graph capture on {@code deviceIndex} (call from TP worker thread).
     *
     * @return {@code true} when capture started.
     */
    public boolean beginCapture(int deviceIndex) {
        if (handle == null || handle.address() == 0) {
            return false;
        }
        Native.cudaGraphCaptureBegin(handle, deviceIndex);
        capturing = true;
        captureBeginNs = System.nanoTime();
        return true;
    }

    /** @return wall time in milliseconds for the last successful capture. */
    public long lastCaptureMs() {
        return lastCaptureMs;
    }

    /** Ends capture and instantiates the graph. No-op when not capturing. */
    public void endCapture() {
        if (!capturing || handle == null || handle.address() == 0) {
            capturing = false;
            return;
        }
        try {
            Native.cudaGraphCaptureEnd(handle);
            ready = Native.cudaGraphIsReady(handle);
            if (ready && captureBeginNs > 0L) {
                lastCaptureMs = (System.nanoTime() - captureBeginNs) / 1_000_000L;
            }
        } finally {
            capturing = false;
            captureBeginNs = 0L;
        }
    }

    /**
     * Replays the captured graph (inputs must already be on device).
     *
     * @param tpRank tensor-parallel rank for one-shot bucket logging.
     */
    public void replay(int tpRank) {
        if (!canReplay(capturedBatch, capturedNumPages)) {
            throw new IllegalStateException("CUDA graph not ready for replay");
        }
        DecodeCudaGraphLog.bucketReplay(tpRank, capturedBatch, capturedNumPages);
        Native.cudaGraphReplay(handle);
    }

    /** @return batch size of the captured bucket, or {@code -1}. */
    public int capturedBatch() {
        return capturedBatch;
    }

    /** @return {@code numPages} of the captured bucket, or {@code -1}. */
    public int capturedNumPages() {
        return capturedNumPages;
    }

    @Override
    public void close() {
        if (handle != null && handle.address() != 0) {
            Native.cudaGraphDestroy(handle);
            handle = MemorySegment.NULL;
        }
        ready = false;
        capturing = false;
    }
}
