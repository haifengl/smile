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
package smile.deep.tensor;

import java.lang.foreign.MemorySegment;
import smile.torch.smile_torch_h;

/**
 * A RAII-style guard that disables gradient calculation for the duration of its
 * lifetime. Use it with try-with-resources:
 *
 * <pre>{@code
 * try (var guard = Tensor.noGradGuard()) {
 *     // inference code; no autograd graph is built
 * }
 * }</pre>
 *
 * The guard is thread-local; it does not affect computation in other threads.
 *
 * @author Haifeng Li
 */
public class NoGradGuard implements AutoCloseable {
    /** The native {@code ST_NoGradGuard} handle. */
    private MemorySegment handle;

    /** Constructor. Disables gradient calculation on the current thread. */
    NoGradGuard() {
        this.handle = Native.check(smile_torch_h.smile_no_grad_guard_create());
    }

    @Override
    public void close() {
        if (handle != null) {
            smile_torch_h.smile_no_grad_guard_free(handle);
            handle = null;
        }
    }
}
