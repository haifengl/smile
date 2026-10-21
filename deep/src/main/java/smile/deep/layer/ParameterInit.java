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

    private static final ThreadLocal<Integer> GEMM_PLACEHOLDER_DEPTH = ThreadLocal.withInitial(() -> 0);

    private ParameterInit() {}

    /**
     * Returns {@code true} inside a {@link #placeholderGemms()} scope.
     * @return {@code true} if {@link LinearLayer#gemm} should skip allocating the weight.
     */
    public static boolean skipGemmWeights() {
        return GEMM_PLACEHOLDER_DEPTH.get() > 0;
    }

    /**
     * Opens a scope in which {@link LinearLayer#gemm} creates a 1x1 placeholder instead of the full weight. Used
     * when quantized replacements (FP8, Marlin) will be installed for those projections, so the dense bf16 shells
     * (twice the size of the FP8 weights) never exist on the device.
     *
     * @return scope handle (close to leave the region).
     */
    public static GemmScope placeholderGemms() {
        GEMM_PLACEHOLDER_DEPTH.set(GEMM_PLACEHOLDER_DEPTH.get() + 1);
        return GemmScope.INSTANCE;
    }

    /** Scope handle of {@link #placeholderGemms()}. */
    public enum GemmScope implements AutoCloseable {
        /** Singleton scope handle. */
        INSTANCE;

        @Override
        public void close() {
            int depth = GEMM_PLACEHOLDER_DEPTH.get() - 1;
            if (depth <= 0) {
                GEMM_PLACEHOLDER_DEPTH.remove();
            } else {
                GEMM_PLACEHOLDER_DEPTH.set(depth);
            }
        }
    }

    /**
     * Returns {@code true} when the current thread is inside an
     * {@link #uninitialized()} / {@link #uninitialized(Device)} scope.
     * @return {@code true} if parameter init should be skipped.
     */
    public static boolean skip() {
        return SKIP_DEPTH.get() > 0;
    }

    /**
     * Target device for empty Linear/Embedding shells in the current scope.
     * Defaults to CPU when not inside a device-aware uninitialized scope.
     * @return target device for empty parameter storage.
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
        /** Singleton scope handle returned by {@link #uninitialized()}. */
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
