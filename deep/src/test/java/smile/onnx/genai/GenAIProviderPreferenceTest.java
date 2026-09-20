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

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.util.OS;

/**
 * Preference / cascade ordering tests (no natives required).
 *
 * @author Haifeng Li
 */
public class GenAIProviderPreferenceTest {

    @AfterEach
    public void clearCache() {
        GenAIProviders.clearUsableCache();
        System.clearProperty(GenAI.PROVIDER_PROPERTY);
    }

    @Test
    public void normalizesAliases() {
        System.setProperty(GenAI.PROVIDER_PROPERTY, "GPU");
        // Env wins over property when set; assume unset in unit test JVM.
        if (System.getenv(GenAI.PROVIDER_ENV) == null) {
            assertEquals("cuda", GenAI.providerPreference());
            System.setProperty(GenAI.PROVIDER_PROPERTY, "hybrid");
            assertEquals("ryzenai", GenAI.providerPreference());
            System.setProperty(GenAI.PROVIDER_PROPERTY, "npu");
            assertEquals("npu", GenAI.providerPreference());
        }
    }

    @Test
    public void autoCascadeOrder() {
        assertEquals(
                List.of("cuda", "ryzenai", "openvino", "qnn", "dml"),
                GenAIProviders.candidateIdsFor("auto"));
    }

    @Test
    public void npuCascadeSkipsCuda() {
        assertEquals(
                List.of("ryzenai", "openvino", "qnn"),
                GenAIProviders.candidateIdsFor("npu"));
    }

    @Test
    public void cpuHasNoAcceleratorCandidates() {
        assertTrue(GenAIProviders.candidateIdsFor("cpu").isEmpty());
    }

    @Test
    public void singleProviderPreferences() {
        assertEquals(List.of("cuda"), GenAIProviders.candidateIdsFor("cuda"));
        assertEquals(List.of("ryzenai"), GenAIProviders.candidateIdsFor("ryzenai"));
        assertEquals(List.of("openvino"), GenAIProviders.candidateIdsFor("openvino"));
        assertEquals(List.of("qnn"), GenAIProviders.candidateIdsFor("qnn"));
        assertEquals(List.of("dml"), GenAIProviders.candidateIdsFor("dml"));
    }

    @Test
    public void directMlNativesOnlyOnWindows() {
        Assumptions.assumeFalse(OS.isWindows());
        assertFalse(GenAIProviders.DIRECT_ML.nativesPresent());
    }

    @Test
    public void usableCacheSkipsFailedCandidate() {
        GenAIProviders.noteUsable("cuda", false);
        assertFalse(GenAIProviders.CUDA.shouldAttempt());
        GenAIProviders.noteUsable("cuda", true);
        assertTrue(GenAIProviders.CUDA.shouldAttempt());
    }
}
