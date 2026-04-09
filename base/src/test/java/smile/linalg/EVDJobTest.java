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
 * Tests for {@link EVDJob}.
 */
public class EVDJobTest {

    @Test
    public void testLapackValues() {
        assertEquals((byte) 'N', EVDJob.NO_VECTORS.lapack());
        assertEquals((byte) 'V', EVDJob.VECTORS.lapack());
    }

    @Test
    public void testDescription() {
        assertNotNull(EVDJob.NO_VECTORS.description());
        assertNotNull(EVDJob.VECTORS.description());
        assertNotEquals(EVDJob.NO_VECTORS.description(), EVDJob.VECTORS.description());
    }

    @Test
    public void testFromLapack() {
        assertSame(EVDJob.NO_VECTORS, EVDJob.fromLapack((byte) 'N'));
        assertSame(EVDJob.VECTORS,    EVDJob.fromLapack((byte) 'V'));
    }

    @Test
    public void testFromLapackUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> EVDJob.fromLapack((byte) 'X'));
    }

    @Test
    public void testOf() {
        assertSame(EVDJob.VECTORS,    EVDJob.of(true));
        assertSame(EVDJob.NO_VECTORS, EVDJob.of(false));
    }

    @Test
    public void testRoundTripLapack() {
        for (EVDJob j : EVDJob.values()) {
            assertSame(j, EVDJob.fromLapack(j.lapack()));
        }
    }

    @Test
    public void testEnumCount() {
        assertEquals(2, EVDJob.values().length);
    }
}

