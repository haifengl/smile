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
 * Tests for {@link EigenRange}.
 */
public class EigenRangeTest {

    @Test
    public void testLapackValues() {
        assertEquals((byte) 'A', EigenRange.ALL.lapack());
        assertEquals((byte) 'V', EigenRange.VALUE.lapack());
        assertEquals((byte) 'I', EigenRange.INDEX.lapack());
    }

    @Test
    public void testDescription() {
        for (EigenRange r : EigenRange.values()) {
            assertNotNull(r.description());
            assertFalse(r.description().isEmpty());
        }
        // All descriptions are distinct
        assertEquals(EigenRange.values().length,
                java.util.Arrays.stream(EigenRange.values())
                        .map(EigenRange::description)
                        .distinct().count());
    }

    @Test
    public void testFromLapack() {
        assertSame(EigenRange.ALL,   EigenRange.fromLapack((byte) 'A'));
        assertSame(EigenRange.VALUE, EigenRange.fromLapack((byte) 'V'));
        assertSame(EigenRange.INDEX, EigenRange.fromLapack((byte) 'I'));
    }

    @Test
    public void testFromLapackUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> EigenRange.fromLapack((byte) 'Z'));
    }

    @Test
    public void testRoundTripLapack() {
        for (EigenRange r : EigenRange.values()) {
            assertSame(r, EigenRange.fromLapack(r.lapack()));
        }
    }

    @Test
    public void testEnumCount() {
        assertEquals(3, EigenRange.values().length);
    }
}

