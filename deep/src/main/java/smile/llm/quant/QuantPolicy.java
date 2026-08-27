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

import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.tensor.Device;

/**
 * Resolves checkpoint {@link QuantFormat} and {@link WeightGemmBackend} for load.
 *
 * @author Haifeng Li
 */
public final class QuantPolicy {
    private static final Logger logger = LoggerFactory.getLogger(QuantPolicy.class);

    /**
     * Resolved quant policy for a checkpoint load.
     *
     * @param format  detected HuggingFace format.
     * @param backend selected GEMM backend.
     */
    public record Resolved(QuantFormat format, WeightGemmBackend backend) {}

    private QuantPolicy() {}

    /**
     * Detects format and selects backend ({@code auto} or explicit override).
     *
     * @param checkpointDir model directory.
     * @param device        target device.
     * @param backendOverride {@code auto}/{@code dense}/{@code fp8}/{@code nvfp4}/{@code marlin},
     *                        or {@code null} to use {@link QuantBackendOverride} / {@code auto}.
     */
    public static Resolved resolve(Path checkpointDir, Device device, String backendOverride)
            throws IOException {
        QuantFormat format = QuantFormatDetector.detect(checkpointDir);
        String override = backendOverride;
        if (override == null || override.isBlank()) {
            override = QuantBackendOverride.get();
        }
        if (override == null || override.isBlank()) {
            override = "auto";
        }
        override = override.trim();
        WeightGemmBackend backend;
        if ("auto".equalsIgnoreCase(override)) {
            backend = WeightGemmBackend.select(device, format);
        } else {
            backend = WeightGemmBackend.valueOf(override.toUpperCase());
            // Still validate format/GPU mismatches for explicit overrides that
            // would silently use Marlin on Hopper.
            if (backend == WeightGemmBackend.MARLIN && format != QuantFormat.GPTQ
                    && format != QuantFormat.AWQ) {
                throw new IllegalStateException(
                        "quant.backend=marlin requires a GPTQ/AWQ checkpoint; got " + format);
            }
            if (backend == WeightGemmBackend.FP8 && format != QuantFormat.FP8) {
                throw new IllegalStateException(
                        "quant.backend=fp8 requires a native FP8 checkpoint; got " + format);
            }
            if (backend == WeightGemmBackend.NVFP4 && format != QuantFormat.NVFP4) {
                throw new IllegalStateException(
                        "quant.backend=nvfp4 requires a native NVFP4 checkpoint; got " + format);
            }
            // Validate GPU capability for the checkpoint format (fail-fast on Hopper+GPTQ).
            if (format != QuantFormat.DENSE) {
                WeightGemmBackend.select(device, format);
            }
        }
        int[] cc = WeightGemmBackend.computeCapability(device);
        logger.info("Weight quant policy: format={} backend={} ({}) sm_{}{}",
                format, backend,
                backend.isFailover() ? "failover" : (backend.isPrimary() ? "primary" : "dense"),
                cc[0], cc[1]);
        return new Resolved(format, backend);
    }
}
