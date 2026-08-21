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

import smile.deep.tensor.Device;

/**
 * Controls whether {@link LinearLayer} / {@link EmbeddingLayer} skip default
 * parameter initialization (Kaiming / normal) and where empty parameter
 * storage is allocated. Inference weight load wraps model construction in
 * {@link #uninitialized(Device)} so shells land on the target GPU without a
 * full host-side empty model footprint.
 *
 * @author Haifeng Li
 */
public final class ParameterInit {
    private static final ThreadLocal<Integer> SKIP_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Device> DEVICE = new ThreadLocal<>();

    private ParameterInit() {}

    /**
     * Returns {@code true} when the current thread is inside an
     * {@link #uninitialized()} / {@link #uninitialized(Device)} scope.
     */
    public static boolean skip() {
        return SKIP_DEPTH.get() > 0;
    }

    /**
     * Target device for empty Linear/Embedding shells in the current scope.
     * Defaults to CPU when not inside a device-aware uninitialized scope.
     */
    public static Device device() {
        Device d = DEVICE.get();
        return d != null ? d : Device.CPU();
    }

    /**
     * Opens a scope where new Linear/Embedding layers skip parameter init and
     * allocate empty storage on CPU.
     *
     * @return scope handle (close to leave the uninitialized region).
     */
    public static Scope uninitialized() {
        return uninitialized(Device.CPU());
    }

    /**
     * Opens a scope where new Linear/Embedding layers skip parameter init and
     * allocate empty storage on {@code device} (e.g. the rank's CUDA device).
     *
     * @param device target device for empty parameter tensors.
     * @return scope handle (close to leave the uninitialized region).
     */
    public static Scope uninitialized(Device device) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        int depth = SKIP_DEPTH.get() + 1;
        SKIP_DEPTH.set(depth);
        if (depth == 1) {
            DEVICE.set(device);
        }
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
                DEVICE.remove();
            } else {
                SKIP_DEPTH.set(depth);
            }
        }
    }
}
