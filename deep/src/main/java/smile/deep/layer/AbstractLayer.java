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
package smile.deep.layer;

import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import smile.torch.Native;

/**
 * Base class for layers backed by a native {@code smile_torch} module
 * (Linear, Conv2d, pooling, normalization, …).
 *
 * <p>Each instance owns two native handles: the typed layer handle (e.g.
 * {@code ST_Linear}) used for {@code forward}, and an {@code ST_Module} view of
 * the same module used for registration into blocks and for device/dtype moves.
 * Both are released together once the wrapper becomes unreachable.
 *
 * @author Haifeng Li
 */
public abstract class AbstractLayer implements Layer {
    /**
     * Bundles the two native handles for a layer with the action that frees
     * them. Built by each subclass's static factory before calling {@code super}.
     * @param handle the typed layer handle (e.g. {@code ST_Linear}).
     * @param module the {@code ST_Module} view of the layer.
     * @param cleanup frees both handles; must not capture the Java wrapper.
     */
    record Handles(MemorySegment handle, MemorySegment module, Runnable cleanup) {
    }

    /** The typed layer handle (e.g. {@code ST_Linear}). */
    final MemorySegment handle;
    /** The {@code ST_Module} view of the layer. */
    final MemorySegment module;
    /** Releases the native handles once this layer becomes unreachable. */
    private final Cleaner.Cleanable cleanable;

    /**
     * Constructor.
     * @param handles the native handles and their cleanup action.
     */
    AbstractLayer(Handles handles) {
        this.handle = handles.handle();
        this.module = handles.module();
        this.cleanable = Native.CLEANER.register(this, handles.cleanup());
    }

    @Override
    public MemorySegment module() {
        return module;
    }

    @Override
    public String toString() {
        return name();
    }
}
