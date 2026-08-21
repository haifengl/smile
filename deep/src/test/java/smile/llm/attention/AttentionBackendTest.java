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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AttentionBackend} / {@link AttentionBackends}.
 */
public class AttentionBackendTest {
    @Test
    public void testGivenIdsWhenParseThenRoundTrip() {
        assertEquals(AttentionBackend.TORCH_NATIVE, AttentionBackend.parse("torch_native"));
        assertEquals(AttentionBackend.FLASHINFER, AttentionBackend.parse("flashinfer"));
        assertEquals(AttentionBackend.TORCH_NATIVE, AttentionBackend.parse("TORCH_NATIVE"));
        assertEquals(AttentionBackend.FLASHINFER, AttentionBackend.parse(""));
        assertEquals(AttentionBackend.FLASHINFER, AttentionBackend.parse(null));
    }

    @Test
    public void testGivenUnknownWhenParseThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> AttentionBackend.parse("fa3"));
    }

    @Test
    public void testGivenTorchNativeWhenInstallThenKernelMatches() {
        AttentionBackends.install(AttentionBackend.TORCH_NATIVE);
        assertEquals(AttentionBackend.TORCH_NATIVE, AttentionBackends.current());
        assertEquals(AttentionBackend.TORCH_NATIVE, AttentionBackends.kernel().backend());
    }

    @Test
    public void testGivenFlashInferUnavailableWhenInstallThenFallsBackToTorchNative() {
        AttentionBackends.install(AttentionBackend.FLASHINFER);
        if (AttentionBackends.flashInferAvailable()) {
            assertEquals(AttentionBackend.FLASHINFER, AttentionBackends.current());
            AttentionBackends.install(AttentionBackend.TORCH_NATIVE);
        } else {
            assertEquals(AttentionBackend.TORCH_NATIVE, AttentionBackends.current());
            assertEquals(AttentionBackend.TORCH_NATIVE, AttentionBackends.kernel().backend());
        }
    }
}
