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
 * Tests for {@link Diag}.
 */
public class DiagTest {

    @Test
    public void testBlasValues() {
        assertEquals(131, Diag.NON_UNIT.blas());
        assertEquals(132, Diag.UNIT.blas());
    }

    @Test
    public void testLapackValues() {
        assertEquals((byte) 'N', Diag.NON_UNIT.lapack());
        assertEquals((byte) 'U', Diag.UNIT.lapack());
    }

    @Test
    public void testDescription() {
        assertNotNull(Diag.NON_UNIT.description());
        assertNotNull(Diag.UNIT.description());
        assertFalse(Diag.NON_UNIT.description().isEmpty());
        assertFalse(Diag.UNIT.description().isEmpty());
        assertNotEquals(Diag.NON_UNIT.description(), Diag.UNIT.description());
    }

    @Test
    public void testFromBlas() {
        assertSame(Diag.NON_UNIT, Diag.fromBlas(131));
        assertSame(Diag.UNIT,     Diag.fromBlas(132));
    }

    @Test
    public void testFromBlasUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Diag.fromBlas(999));
    }

    @Test
    public void testFromLapack() {
        assertSame(Diag.NON_UNIT, Diag.fromLapack((byte) 'N'));
        assertSame(Diag.UNIT,     Diag.fromLapack((byte) 'U'));
    }

    @Test
    public void testFromLapackUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Diag.fromLapack((byte) 'X'));
    }

    @Test
    public void testRoundTripBlas() {
        for (Diag d : Diag.values()) {
            assertSame(d, Diag.fromBlas(d.blas()));
        }
    }

    @Test
    public void testRoundTripLapack() {
        for (Diag d : Diag.values()) {
            assertSame(d, Diag.fromLapack(d.lapack()));
        }
    }

    @Test
    public void testEnumCount() {
        assertEquals(2, Diag.values().length);
    }
}

