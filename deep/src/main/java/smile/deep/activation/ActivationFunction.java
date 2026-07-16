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
package smile.deep.activation;

import java.io.Serializable;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.layer.Layer;
import smile.torch.Native;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;

/**
 * The activation function. It also implements the layer interface
 * so that it can be treated added into a network as a layer.
 *
 * @author Haifeng Li
 */
public abstract class ActivationFunction implements Layer, Serializable {
    /** The module of activation function ({@code ST_Module}). */
    final transient MemorySegment module;
    /** The function name. */
    final String name;
    /** True if the operation executes in-place. */
    final boolean inplace;

    /**
     * Constructor.
     * @param name the function name.
     * @param inplace true if the operation executes in-place.
     */
    public ActivationFunction(String name, boolean inplace) {
        this.name = name;
        this.inplace = inplace;
        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(arena.allocateFrom(name)));
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Returns the name of activation function.
     * @return the name of activation function.
     */
    public String name() {
        return name;
    }

    /**
     * Returns true if the operation executes in-place.
     * @return true if the operation executes in-place.
     */
    public boolean isInplace() {
        return inplace;
    }

    @Override
    public MemorySegment module() {
        return module;
    }
}
