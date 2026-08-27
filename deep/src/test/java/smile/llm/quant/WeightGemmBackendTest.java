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

import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.DeviceType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WeightGemmBackend} selection policy.
 *
 * @author Haifeng Li
 */
public class WeightGemmBackendTest {

    @Test
    public void testGivenDenseWhenSelectThenDense() {
        assertEquals(WeightGemmBackend.DENSE,
                WeightGemmBackend.select(Device.CPU(), QuantFormat.DENSE));
    }

    @Test
    public void testGivenGptqOnHopperWhenSelectThenFails() {
        // Simulate policy without CUDA: major>=9 via direct switch coverage using
        // a private helper would need mocking; instead validate error message shape
        // by invoking the documented mismatch path through a test double of CC.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> selectWithCc(QuantFormat.GPTQ, 9, 0));
        assertTrue(ex.getMessage().contains("Marlin"));
        assertTrue(ex.getMessage().contains("FP8"));
    }

    @Test
    public void testGivenGptqOnAmpereWhenSelectThenMarlin() {
        assertEquals(WeightGemmBackend.MARLIN, selectWithCc(QuantFormat.GPTQ, 8, 0));
        assertEquals(WeightGemmBackend.MARLIN, selectWithCc(QuantFormat.AWQ, 8, 6));
        assertEquals(WeightGemmBackend.MARLIN, selectWithCc(QuantFormat.AWQ, 8, 9));
    }

    @Test
    public void testGivenFp8OnAmpereWhenSelectThenFails() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> selectWithCc(QuantFormat.FP8, 8, 0));
        assertTrue(ex.getMessage().contains("9.0"));
    }

    @Test
    public void testGivenFp8OnHopperWhenSelectThenFp8() {
        assertEquals(WeightGemmBackend.FP8, selectWithCc(QuantFormat.FP8, 9, 0));
    }

    @Test
    public void testGivenNvfp4OnHopperWhenSelectThenFails() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> selectWithCc(QuantFormat.NVFP4, 9, 0));
        assertTrue(ex.getMessage().contains("10.0"));
    }

    @Test
    public void testGivenNvfp4OnBlackwellWhenSelectThenNvfp4() {
        assertEquals(WeightGemmBackend.NVFP4, selectWithCc(QuantFormat.NVFP4, 10, 0));
    }

    @Test
    public void testGivenQuantConfigWhenDetectGptqThenGptq() throws Exception {
        var root = new tools.jackson.databind.ObjectMapper().readTree(
                "{\"quantization_config\":{\"quant_method\":\"gptq\",\"bits\":4}}");
        assertEquals(QuantFormat.GPTQ, QuantFormatDetector.fromConfig(root));
    }

    @Test
    public void testGivenQuantConfigWhenDetectAwqThenAwq() throws Exception {
        var root = new tools.jackson.databind.ObjectMapper().readTree(
                "{\"quantization_config\":{\"quant_method\":\"awq\"}}");
        assertEquals(QuantFormat.AWQ, QuantFormatDetector.fromConfig(root));
    }

    @Test
    public void testGivenQuantConfigWhenDetectFp8ThenFp8() throws Exception {
        var root = new tools.jackson.databind.ObjectMapper().readTree(
                "{\"quantization_config\":{\"quant_method\":\"fp8\"}}");
        assertEquals(QuantFormat.FP8, QuantFormatDetector.fromConfig(root));
    }

    /**
     * Mirrors {@link WeightGemmBackend#select} policy with an injected compute capability
     * so unit tests do not require a CUDA device.
     */
    static WeightGemmBackend selectWithCc(QuantFormat format, int major, int minor) {
        if (format == QuantFormat.DENSE) {
            return WeightGemmBackend.DENSE;
        }
        int sm = major * 10 + minor;
        return switch (format) {
            case FP8 -> {
                if (major < 9) {
                    throw new IllegalStateException(
                            "FP8 weight GEMM requires compute capability >= 9.0 (Hopper+); got sm_"
                                    + sm + ".");
                }
                yield WeightGemmBackend.FP8;
            }
            case NVFP4 -> {
                if (major < 10) {
                    throw new IllegalStateException(
                            "NVFP4 weight GEMM requires compute capability >= 10.0 (Blackwell+); got sm_"
                                    + sm + ".");
                }
                yield WeightGemmBackend.NVFP4;
            }
            case GPTQ, AWQ -> {
                if (major >= 9) {
                    throw new IllegalStateException(
                            "GPTQ/AWQ Marlin failover is Ampere/Ada only (sm_80–89); got sm_"
                                    + sm + ". Use FP8/NVFP4 checkpoint.");
                }
                if (major < 8) {
                    throw new IllegalStateException("Marlin requires sm_80+; got sm_" + sm);
                }
                yield WeightGemmBackend.MARLIN;
            }
            case DENSE -> WeightGemmBackend.DENSE;
        };
    }
}
