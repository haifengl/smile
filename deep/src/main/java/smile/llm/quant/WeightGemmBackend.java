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
package smile.llm.quant;

import smile.deep.tensor.Device;
import smile.torch.Native;

/**
 * Weight GEMM backend selected from checkpoint format and GPU capability.
 *
 * <p>Policy (locked):
 * <ul>
 *   <li>Dense → {@link #DENSE}</li>
 *   <li>Native FP8 on sm_90+ → {@link #FP8}</li>
 *   <li>Native NVFP4 on sm_100+ → {@link #NVFP4}</li>
 *   <li>GPTQ/AWQ on Ampere/Ada (8.0–8.9) → {@link #MARLIN}</li>
 *   <li>GPTQ/AWQ on Hopper/Blackwell → fail fast (no Marlin)</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public enum WeightGemmBackend {
    DENSE,
    FP8,
    NVFP4,
    MARLIN;

    /**
     * Selects the weight GEMM backend.
     *
     * @param device CUDA (or CPU) device that will run inference.
     * @param format checkpoint quantization format.
     * @return the backend to use.
     * @throws IllegalStateException on unsupported format/GPU combinations.
     */
    public static WeightGemmBackend select(Device device, QuantFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        if (format == QuantFormat.DENSE) {
            return DENSE;
        }

        int[] cc = computeCapability(device);
        int major = cc[0];
        int minor = cc[1];
        int sm = major * 10 + minor;

        return switch (format) {
            case FP8 -> {
                if (major < 9) {
                    throw new IllegalStateException(
                            "FP8 weight GEMM requires compute capability >= 9.0 (Hopper+); got sm_"
                                    + sm + ". Use a GPTQ/AWQ checkpoint with Marlin on Ampere/Ada, "
                                    + "or a dense BF16/FP16 checkpoint.");
                }
                yield FP8;
            }
            case NVFP4 -> {
                if (major < 10) {
                    throw new IllegalStateException(
                            "NVFP4 weight GEMM requires compute capability >= 10.0 (Blackwell+); got sm_"
                                    + sm + ".");
                }
                yield NVFP4;
            }
            case GPTQ, AWQ -> {
                if (major >= 9) {
                    throw new IllegalStateException(
                            "GPTQ/AWQ Marlin failover is Ampere/Ada only (sm_80–89); got sm_"
                                    + sm + ". Use a native FP8 (Hopper+) or NVFP4 (Blackwell+) "
                                    + "checkpoint instead of Marlin on this GPU.");
                }
                if (major < 8) {
                    throw new IllegalStateException(
                            "Marlin requires compute capability >= 8.0; got sm_" + sm);
                }
                yield MARLIN;
            }
            case DENSE -> DENSE;
        };
    }

    /**
     * Returns {@code {major, minor}} for {@code device}, or {@code {0,0}} on CPU.
     */
    public static int[] computeCapability(Device device) {
        if (device == null || !device.isCUDA()) {
            return new int[]{0, 0};
        }
        int index = device.index() >= 0 ? device.index() : 0;
        return Native.cudaComputeCapability(index);
    }

    /** @return {@code true} when this backend is the Ampere/Ada INT4 failover. */
    public boolean isFailover() {
        return this == MARLIN;
    }

    /** @return {@code true} when this is a primary native low-precision path. */
    public boolean isPrimary() {
        return this == FP8 || this == NVFP4;
    }
}
