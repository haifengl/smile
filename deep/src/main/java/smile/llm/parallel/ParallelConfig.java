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
package smile.llm.parallel;

import java.util.Arrays;
import java.util.Objects;

/**
 * Process-mesh configuration for tensor / pipeline / data parallelism.
 *
 * <p>{@code worldSize == tpSize * ppSize * dpSize}. Phase 1 uses {@code ppSize=1}
 * and {@code dpSize=1}; pipeline axes are reserved for multi-node PP.
 *
 * @param tpSize   tensor-parallel size (must divide attention / FFN widths).
 * @param ppSize   pipeline-parallel size (must be {@code 1} until phase 2).
 * @param dpSize   data-parallel size (must be {@code 1} in phase 1).
 * @param devices  CUDA device indices for local TP ranks ({@code length == tpSize}).
 *
 * @author Haifeng Li
 */
public record ParallelConfig(int tpSize, int ppSize, int dpSize, byte[] devices) {
    public ParallelConfig {
        if (tpSize < 1) throw new IllegalArgumentException("tpSize must be >= 1");
        if (ppSize < 1) throw new IllegalArgumentException("ppSize must be >= 1");
        if (dpSize < 1) throw new IllegalArgumentException("dpSize must be >= 1");
        if (ppSize > 1) {
            throw new IllegalArgumentException("pipeline_parallel_size > 1 is not supported yet");
        }
        if (dpSize > 1) {
            throw new IllegalArgumentException("data_parallel_size > 1 is not supported yet");
        }
        Objects.requireNonNull(devices, "devices");
        if (devices.length != tpSize) {
            throw new IllegalArgumentException(
                    "devices.length (" + devices.length + ") must equal tpSize (" + tpSize + ")");
        }
        devices = Arrays.copyOf(devices, devices.length);
    }

    /** Single-device (no parallelism). */
    public static ParallelConfig single(byte device) {
        return new ParallelConfig(1, 1, 1, new byte[]{device});
    }

    /** Tensor-parallel group on the given CUDA devices. */
    public static ParallelConfig tensorParallel(byte... devices) {
        if (devices == null || devices.length < 1) {
            throw new IllegalArgumentException("devices required");
        }
        return new ParallelConfig(devices.length, 1, 1, devices);
    }

    public int worldSize() {
        return tpSize * ppSize * dpSize;
    }

    public boolean isTensorParallel() {
        return tpSize > 1;
    }
}
