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

/**
 * Controls whether {@link LinearLayer} / {@link EmbeddingLayer} skip default
 * parameter initialization (Kaiming / normal). Inference weight load wraps
 * model construction in {@link #uninitialized()} so empty tensors are
 * allocated without filling random values that safetensors immediately
 * overwrite.
 *
 * @author Haifeng Li
 */
public final class ParameterInit {
    private static final ThreadLocal<Integer> SKIP_DEPTH = ThreadLocal.withInitial(() -> 0);

    private ParameterInit() {}

    /**
     * Returns {@code true} when the current thread is inside an
     * {@link #uninitialized()} scope.
     */
    public static boolean skip() {
        return SKIP_DEPTH.get() > 0;
    }

    /**
     * Opens a scope where new Linear/Embedding layers skip parameter init.
     * Nesting is supported; restore happens when the returned handle is closed.
     *
     * @return scope handle (close to leave the uninitialized region).
     */
    public static Scope uninitialized() {
        SKIP_DEPTH.set(SKIP_DEPTH.get() + 1);
        return Scope.INSTANCE;
    }

    /**
     * Decrements the skip depth when closed. {@code close()} does not throw.
     */
    public enum Scope implements AutoCloseable {
        INSTANCE;

        @Override
        public void close() {
            int depth = SKIP_DEPTH.get() - 1;
            if (depth <= 0) {
                SKIP_DEPTH.remove();
            } else {
                SKIP_DEPTH.set(depth);
            }
        }
    }
}
