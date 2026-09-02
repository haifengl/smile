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
package smile.llm.attention;

import java.lang.foreign.MemorySegment;
import smile.torch.Native;

/**
 * Opaque FlashInfer workspace / plan handle (one per device / TP rank).
 *
 * @author Haifeng Li
 */
public final class FlashInferWorkspace implements AutoCloseable {
    private MemorySegment handle;

    FlashInferWorkspace(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Allocates a workspace on the given CUDA device.
     *
     * @param deviceIndex CUDA device ordinal.
     * @param workspaceBytes scratch buffer size hint (0 = kernel default).
     * @return workspace, or {@code null} when FlashInfer is not compiled in.
     */
    public static FlashInferWorkspace create(int deviceIndex, long workspaceBytes) {
        MemorySegment h = Native.flashInferWorkspaceCreate(deviceIndex, workspaceBytes);
        if (h == null || h.address() == 0) {
            return null;
        }
        return new FlashInferWorkspace(h);
    }

    /** @return native handle for downcalls. */
    public MemorySegment handle() {
        return handle;
    }

    @Override
    public void close() {
        if (handle != null && handle.address() != 0) {
            Native.flashInferWorkspaceFree(handle);
            handle = MemorySegment.NULL;
        }
    }

    /**
     * Invalidates step-scoped decode-plan and prefill-gather caches on this workspace.
     * Safe to call when FlashInfer is unavailable (no-op).
     */
    public void invalidateRuntimeCache() {
        if (handle != null && handle.address() != 0) {
            Native.flashInferWorkspaceInvalidateRuntimeCache(handle);
        }
    }
}
