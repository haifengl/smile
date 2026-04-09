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
package smile.linalg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Transpose}.
 */
public class TransposeTest {

    @Test
    public void testBlasValues() {
        assertEquals(111, Transpose.NO_TRANSPOSE.blas());
        assertEquals(112, Transpose.TRANSPOSE.blas());
        assertEquals(113, Transpose.CONJUGATE_TRANSPOSE.blas());
    }

    @Test
    public void testLapackValues() {
        assertEquals((byte) 'N', Transpose.NO_TRANSPOSE.lapack());
        assertEquals((byte) 'T', Transpose.TRANSPOSE.lapack());
        assertEquals((byte) 'C', Transpose.CONJUGATE_TRANSPOSE.lapack());
    }

    @Test
    public void testDescription() {
        for (Transpose t : Transpose.values()) {
            assertNotNull(t.description());
            assertFalse(t.description().isEmpty());
        }
        assertEquals(Transpose.values().length,
                java.util.Arrays.stream(Transpose.values())
                        .map(Transpose::description)
                        .distinct().count());
    }

    // -----------------------------------------------------------------------
    // flip()
    // -----------------------------------------------------------------------

    @Test
    public void testFlipNoTranspose() {
        assertSame(Transpose.TRANSPOSE, Transpose.flip(Transpose.NO_TRANSPOSE));
    }

    @Test
    public void testFlipTranspose() {
        assertSame(Transpose.NO_TRANSPOSE, Transpose.flip(Transpose.TRANSPOSE));
    }

    @Test
    public void testFlipConjugateTranspose() {
        // Conjugate-transpose flips to NO_TRANSPOSE
        assertSame(Transpose.NO_TRANSPOSE, Transpose.flip(Transpose.CONJUGATE_TRANSPOSE));
    }

    @Test
    public void testFlipNull() {
        assertNull(Transpose.flip(null));
    }

    @Test
    public void testFlipIsInvolutionForNoTransposeAndTranspose() {
        assertSame(Transpose.NO_TRANSPOSE,
                Transpose.flip(Transpose.flip(Transpose.NO_TRANSPOSE)));
        assertSame(Transpose.TRANSPOSE,
                Transpose.flip(Transpose.flip(Transpose.TRANSPOSE)));
    }

    // -----------------------------------------------------------------------
    // fromBlas / fromLapack
    // -----------------------------------------------------------------------

    @Test
    public void testFromBlas() {
        assertSame(Transpose.NO_TRANSPOSE,        Transpose.fromBlas(111));
        assertSame(Transpose.TRANSPOSE,           Transpose.fromBlas(112));
        assertSame(Transpose.CONJUGATE_TRANSPOSE, Transpose.fromBlas(113));
    }

    @Test
    public void testFromBlasUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Transpose.fromBlas(999));
    }

    @Test
    public void testFromLapack() {
        assertSame(Transpose.NO_TRANSPOSE,        Transpose.fromLapack((byte) 'N'));
        assertSame(Transpose.TRANSPOSE,           Transpose.fromLapack((byte) 'T'));
        assertSame(Transpose.CONJUGATE_TRANSPOSE, Transpose.fromLapack((byte) 'C'));
    }

    @Test
    public void testFromLapackUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Transpose.fromLapack((byte) 'X'));
    }

    @Test
    public void testRoundTripBlas() {
        for (Transpose t : Transpose.values()) {
            assertSame(t, Transpose.fromBlas(t.blas()));
        }
    }

    @Test
    public void testRoundTripLapack() {
        for (Transpose t : Transpose.values()) {
            assertSame(t, Transpose.fromLapack(t.lapack()));
        }
    }

    @Test
    public void testEnumCount() {
        assertEquals(3, Transpose.values().length);
    }
}

