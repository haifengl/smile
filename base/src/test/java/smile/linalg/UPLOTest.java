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
 * Tests for {@link UPLO}.
 */
public class UPLOTest {

    @Test
    public void testBlasValues() {
        assertEquals(121, UPLO.UPPER.blas());
        assertEquals(122, UPLO.LOWER.blas());
    }

    @Test
    public void testLapackValues() {
        assertEquals((byte) 'U', UPLO.UPPER.lapack());
        assertEquals((byte) 'L', UPLO.LOWER.lapack());
    }

    @Test
    public void testIsUpper() {
        assertTrue(UPLO.UPPER.isUpper());
        assertFalse(UPLO.LOWER.isUpper());
    }

    @Test
    public void testIsLower() {
        assertTrue(UPLO.LOWER.isLower());
        assertFalse(UPLO.UPPER.isLower());
    }

    @Test
    public void testIsUpperAndIsLowerAreMutuallyExclusive() {
        for (UPLO u : UPLO.values()) {
            assertNotEquals(u.isUpper(), u.isLower(),
                    "isUpper and isLower must be mutually exclusive for " + u);
        }
    }

    @Test
    public void testDescription() {
        assertNotNull(UPLO.UPPER.description());
        assertNotNull(UPLO.LOWER.description());
        assertNotEquals(UPLO.UPPER.description(), UPLO.LOWER.description());
    }

    // -----------------------------------------------------------------------
    // flip()
    // -----------------------------------------------------------------------

    @Test
    public void testFlipUpper() {
        assertSame(UPLO.LOWER, UPLO.flip(UPLO.UPPER));
    }

    @Test
    public void testFlipLower() {
        assertSame(UPLO.UPPER, UPLO.flip(UPLO.LOWER));
    }

    @Test
    public void testFlipNull() {
        assertNull(UPLO.flip(null));
    }

    @Test
    public void testFlipIsInvolution() {
        for (UPLO u : UPLO.values()) {
            assertSame(u, UPLO.flip(UPLO.flip(u)),
                    "flip(flip(x)) should equal x for " + u);
        }
    }

    // -----------------------------------------------------------------------
    // fromBlas / fromLapack
    // -----------------------------------------------------------------------

    @Test
    public void testFromBlas() {
        assertSame(UPLO.UPPER, UPLO.fromBlas(121));
        assertSame(UPLO.LOWER, UPLO.fromBlas(122));
    }

    @Test
    public void testFromBlasUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> UPLO.fromBlas(999));
    }

    @Test
    public void testFromLapack() {
        assertSame(UPLO.UPPER, UPLO.fromLapack((byte) 'U'));
        assertSame(UPLO.LOWER, UPLO.fromLapack((byte) 'L'));
    }

    @Test
    public void testFromLapackUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> UPLO.fromLapack((byte) 'X'));
    }

    @Test
    public void testRoundTripBlas() {
        for (UPLO u : UPLO.values()) {
            assertSame(u, UPLO.fromBlas(u.blas()));
        }
    }

    @Test
    public void testRoundTripLapack() {
        for (UPLO u : UPLO.values()) {
            assertSame(u, UPLO.fromLapack(u.lapack()));
        }
    }

    @Test
    public void testEnumCount() {
        assertEquals(2, UPLO.values().length);
    }
}

