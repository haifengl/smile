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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.torch.Native;

/**
 * Per-device CUDA graph session for the uniform MTP window-verify forward.
 *
 * <p>Graphs are bucketed by {@code (batch, windowLen, numPages)} — unlike
 * {@link DecodeCudaGraphSession} (implicit {@code S == 1}), verify's query
 * length is a real bucket dimension. Within a bucket, the flat KV write-index
 * buffer and CSR metadata are updated in place before {@link #replay(int)}.
 *
 * <p>Stage 4 of the verify-CUDA-graph plan drives this session's
 * {@link #shouldCapture} bookkeeping against real traffic while
 * {@link VerifyCudaGraph#captureEnabled()} is {@code false}, so
 * {@link #beginCapture} is never actually invoked yet — only bucket-key
 * transitions and warmup counting are exercised for real.
 *
 * @author Haifeng Li
 */
public final class VerifyCudaGraphSession implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(VerifyCudaGraphSession.class);
    private static final Set<Long> WARMUP_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> CAPTURE_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> REPLAY_LOGGED = ConcurrentHashMap.newKeySet();

    private MemorySegment handle;
    private int capturedBatch = -1;
    private int capturedWindowLen = -1;
    private int capturedNumPages = -1;
    private int warmupRemaining = VerifyCudaGraph.warmupSteps();
    private boolean ready;
    private boolean capturing;
    private long captureBeginNs;
    private long lastCaptureMs;

    /**
     * Creates a session when verify CUDA graph wiring is enabled.
     *
     * @return new session, or {@code null} when unavailable.
     */
    public static VerifyCudaGraphSession tryCreate() {
        if (!VerifyCudaGraph.enabled()) {
            return null;
        }
        MemorySegment h = Native.cudaGraphCreate();
        if (h == null || h.address() == 0) {
            return null;
        }
        return new VerifyCudaGraphSession(h);
    }

    private VerifyCudaGraphSession(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Returns whether a graph for {@code (batch, windowLen, numPages)} can be replayed.
     *
     * @param batch     verify batch size.
     * @param windowLen query length ({@code numDrafts + 1}).
     * @param numPages  KV page count for this verify step.
     * @return {@code true} when a graph for this bucket can be replayed.
     */
    public boolean canReplay(int batch, int windowLen, int numPages) {
        return ready && !capturing
                && capturedBatch == batch
                && capturedWindowLen == windowLen
                && capturedNumPages == numPages;
    }

    /**
     * Marks one eager warmup step for the current bucket.
     *
     * @param batch     verify batch size.
     * @param windowLen query length ({@code numDrafts + 1}).
     * @param numPages  KV page count for this verify step.
     * @param tpRank    tensor-parallel rank for logging ({@code >= 0}).
     * @return {@code true} when the next forward should capture a new graph.
     */
    public boolean shouldCapture(int batch, int windowLen, int numPages, int tpRank) {
        if (ready && capturedBatch == batch && capturedWindowLen == windowLen
                && capturedNumPages == numPages) {
            return false;
        }
        if (capturedBatch != batch || capturedWindowLen != windowLen || capturedNumPages != numPages) {
            resetForNewBucket(batch, windowLen, numPages);
        }
        if (warmupRemaining > 0) {
            int step = VerifyCudaGraph.warmupSteps() - warmupRemaining + 1;
            int total = VerifyCudaGraph.warmupSteps();
            long key = bucketKey(batch, windowLen, numPages);
            if (step == 1 && logger.isInfoEnabled() && WARMUP_LOGGED.add(key)) {
                logger.info("tpRank={}: verify CUDA graph bucket warmup batch={} windowLen={} "
                        + "numPages={} ({} eager steps before capture)",
                        tpRank, batch, windowLen, numPages, total);
            }
            warmupRemaining--;
            return false;
        }
        return true;
    }

    private void resetForNewBucket(int batch, int windowLen, int numPages) {
        ready = false;
        capturing = false;
        capturedBatch = batch;
        capturedWindowLen = windowLen;
        capturedNumPages = numPages;
        warmupRemaining = VerifyCudaGraph.warmupSteps();
    }

    /**
     * Begins CUDA graph capture on {@code deviceIndex} (call from TP worker thread).
     *
     * @param deviceIndex CUDA device ordinal for capture.
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

    /**
     * Returns wall time in milliseconds for the last successful capture.
     *
     * @return capture duration in milliseconds.
     */
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
     * Logs (once per bucket) a successful graph capture.
     *
     * @param tpRank tensor-parallel rank for logging.
     */
    public void logCapture(int tpRank) {
        if (!logger.isInfoEnabled()
                || !CAPTURE_LOGGED.add(bucketKey(capturedBatch, capturedWindowLen, capturedNumPages))) {
            return;
        }
        logger.info("tpRank={}: verify CUDA graph bucket capture batch={} windowLen={} numPages={} ms={}",
                tpRank, capturedBatch, capturedWindowLen, capturedNumPages, lastCaptureMs);
    }

    /**
     * Replays the captured graph (inputs must already be on device).
     *
     * @param tpRank tensor-parallel rank for one-shot bucket logging.
     */
    public void replay(int tpRank) {
        if (!canReplay(capturedBatch, capturedWindowLen, capturedNumPages)) {
            throw new IllegalStateException("verify CUDA graph not ready for replay");
        }
        if (logger.isInfoEnabled()
                && REPLAY_LOGGED.add(bucketKey(capturedBatch, capturedWindowLen, capturedNumPages))) {
            logger.info("tpRank={}: verify CUDA graph bucket replay batch={} windowLen={} numPages={}",
                    tpRank, capturedBatch, capturedWindowLen, capturedNumPages);
        }
        Native.cudaGraphReplay(handle);
    }

    /**
     * Returns the batch size of the captured bucket.
     *
     * @return batch size, or {@code -1} when none captured.
     */
    public int capturedBatch() {
        return capturedBatch;
    }

    /**
     * Returns the {@code windowLen} of the captured bucket.
     *
     * @return window length, or {@code -1} when none captured.
     */
    public int capturedWindowLen() {
        return capturedWindowLen;
    }

    /**
     * Returns the {@code numPages} of the captured bucket.
     *
     * @return page count, or {@code -1} when none captured.
     */
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

    private static long bucketKey(int batch, int windowLen, int numPages) {
        return ((long) batch << 40) | ((long) windowLen << 20) | (numPages & 0xfffffL);
    }
}
