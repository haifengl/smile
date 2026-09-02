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
 * Per-device CUDA graph session for batch-1 decode.
 *
 * <p>Graphs are bucketed by KV page count ({@code numPages}). Within a page bucket,
 * {@code last_page_len} is updated in place before {@link #replay()}.
 *
 * @author Haifeng Li
 */
public final class DecodeCudaGraphSession implements AutoCloseable {
    private MemorySegment handle;
    private int capturedNumPages = -1;
    private int warmupRemaining = DecodeCudaGraph.warmupSteps();
    private boolean ready;
    private boolean capturing;

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

    /** @return {@code true} when a graph for {@code numPages} can be replayed. */
    public boolean canReplay(int numPages) {
        return ready && !capturing && capturedNumPages == numPages;
    }

    /**
     * Marks one eager warmup step for the current page bucket.
     *
     * @param numPages KV page count for this decode step.
     * @return {@code true} when the next forward should capture a new graph.
     */
    public boolean shouldCapture(int numPages) {
        if (ready && capturedNumPages == numPages) {
            return false;
        }
        if (capturedNumPages != numPages) {
            resetForNewBucket(numPages);
        }
        if (warmupRemaining > 0) {
            warmupRemaining--;
            return false;
        }
        return true;
    }

    private void resetForNewBucket(int numPages) {
        ready = false;
        capturing = false;
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
        return true;
    }

    /** Ends capture and instantiates the graph. */
    public void endCapture() {
        if (!capturing || handle == null || handle.address() == 0) {
            return;
        }
        Native.cudaGraphCaptureEnd(handle);
        capturing = false;
        ready = Native.cudaGraphIsReady(handle);
    }

    /** Replays the captured graph (inputs must already be on device). */
    public void replay() {
        if (!canReplay(capturedNumPages)) {
            throw new IllegalStateException("CUDA graph not ready for replay");
        }
        Native.cudaGraphReplay(handle);
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
