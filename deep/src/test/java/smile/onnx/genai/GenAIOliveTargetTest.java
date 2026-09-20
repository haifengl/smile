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
package smile.onnx.genai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Olive target mapping tests (no Olive CLI).
 *
 * @author Haifeng Li
 */
public class GenAIOliveTargetTest {

    @AfterEach
    public void clear() {
        GenAIProviders.clearUsableCache();
        System.clearProperty(GenAI.PROVIDER_PROPERTY);
    }

    @Test
    public void mapsCascadeIds() {
        assertEquals(new GenAIOliveTarget("cuda", "gpu", "CUDAExecutionProvider", "fp8"),
                GenAI.toOliveTarget("cuda"));
        assertEquals(new GenAIOliveTarget("dml", "gpu", "DmlExecutionProvider", "int4"),
                GenAI.toOliveTarget("dml"));
        assertEquals(new GenAIOliveTarget("openvino", "npu", "OpenVINOExecutionProvider", "int4"),
                GenAI.toOliveTarget("openvino"));
        assertEquals(new GenAIOliveTarget("cpu", "cpu", "CPUExecutionProvider", "int4"),
                GenAI.toOliveTarget("cpu"));
    }

    @Test
    public void supportsFp8OnlyCuda() {
        assertTrue(GenAI.supportsFp8("cuda"));
        assertFalse(GenAI.supportsFp8("dml"));
        assertFalse(GenAI.supportsFp8("cpu"));
    }

    @Test
    public void cpuPreferenceForcesCpuTarget() {
        if (System.getenv(GenAI.PROVIDER_ENV) != null) {
            return; // env wins over property
        }
        System.setProperty(GenAI.PROVIDER_PROPERTY, "cpu");
        GenAIOliveTarget t = GenAI.resolveOliveTarget();
        assertEquals("cpu", t.candidateId());
        assertEquals("int4", t.defaultPrecision());
        assertFalse(t.prefersFp8());
    }
}
