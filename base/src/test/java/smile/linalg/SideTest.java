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
 * Tests for {@link Side}.
 */
public class SideTest {

    @Test
    public void testBlasValues() {
        assertEquals(141, Side.LEFT.blas());
        assertEquals(142, Side.RIGHT.blas());
    }

    @Test
    public void testLapackValues() {
        assertEquals((byte) 'L', Side.LEFT.lapack());
        assertEquals((byte) 'R', Side.RIGHT.lapack());
    }

    @Test
    public void testDescription() {
        assertNotNull(Side.LEFT.description());
        assertNotNull(Side.RIGHT.description());
        assertNotEquals(Side.LEFT.description(), Side.RIGHT.description());
    }

    @Test
    public void testFromBlas() {
        assertSame(Side.LEFT,  Side.fromBlas(141));
        assertSame(Side.RIGHT, Side.fromBlas(142));
    }

    @Test
    public void testFromBlasUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Side.fromBlas(999));
    }

    @Test
    public void testFromLapack() {
        assertSame(Side.LEFT,  Side.fromLapack((byte) 'L'));
        assertSame(Side.RIGHT, Side.fromLapack((byte) 'R'));
    }

    @Test
    public void testFromLapackUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Side.fromLapack((byte) 'X'));
    }

    @Test
    public void testRoundTripBlas() {
        for (Side s : Side.values()) {
            assertSame(s, Side.fromBlas(s.blas()));
        }
    }

    @Test
    public void testRoundTripLapack() {
        for (Side s : Side.values()) {
            assertSame(s, Side.fromLapack(s.lapack()));
        }
    }

    @Test
    public void testEnumCount() {
        assertEquals(2, Side.values().length);
    }
}

