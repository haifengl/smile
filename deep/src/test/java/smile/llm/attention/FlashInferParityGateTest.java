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
package smile.llm.attention;

import org.junit.jupiter.api.Test;
import smile.torch.Native;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gates for FlashInfer availability / AOT wiring (CPU-safe).
 *
 * <p>Full Llama greedy parity vs {@code torch_native} requires a CUDA GPU and
 * is exercised in the serve Docker image / A100 smoke (see plan).
 */
public class FlashInferParityGateTest {
    @Test
    public void testGivenNativeWhenSetAotDirThenDoesNotThrow() {
        assertDoesNotThrow(() -> Native.flashInferSetAotDir(null));
        assertDoesNotThrow(() -> Native.flashInferSetAotDir(""));
    }

    @Test
    public void testGivenArtifactsHelperWhenBundledConstantThenNonEmpty() {
        assertFalse(FlashInferArtifacts.BUNDLED_AOT_DIR.isBlank());
        assertFalse(FlashInferArtifacts.DEFAULT_JIT_CACHE_VERSION.isBlank());
    }
}
