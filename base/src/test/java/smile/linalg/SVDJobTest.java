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
 * Tests for {@link SVDJob}.
 */
public class SVDJobTest {

    @Test
    public void testLapackValues() {
        assertEquals((byte) 'A', SVDJob.ALL.lapack());
        assertEquals((byte) 'S', SVDJob.COMPACT.lapack());
        assertEquals((byte) 'O', SVDJob.OVERWRITE.lapack());
        assertEquals((byte) 'N', SVDJob.NO_VECTORS.lapack());
    }

    @Test
    public void testDescription() {
        for (SVDJob j : SVDJob.values()) {
            assertNotNull(j.description());
            assertFalse(j.description().isEmpty());
        }
        // All descriptions distinct
        assertEquals(SVDJob.values().length,
                java.util.Arrays.stream(SVDJob.values())
                        .map(SVDJob::description)
                        .distinct().count());
    }

    @Test
    public void testFromLapack() {
        assertSame(SVDJob.ALL,        SVDJob.fromLapack((byte) 'A'));
        assertSame(SVDJob.COMPACT,    SVDJob.fromLapack((byte) 'S'));
        assertSame(SVDJob.OVERWRITE,  SVDJob.fromLapack((byte) 'O'));
        assertSame(SVDJob.NO_VECTORS, SVDJob.fromLapack((byte) 'N'));
    }

    @Test
    public void testFromLapackUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> SVDJob.fromLapack((byte) 'Z'));
    }

    @Test
    public void testOf() {
        assertSame(SVDJob.COMPACT,    SVDJob.of(true));
        assertSame(SVDJob.NO_VECTORS, SVDJob.of(false));
    }

    @Test
    public void testRoundTripLapack() {
        for (SVDJob j : SVDJob.values()) {
            assertSame(j, SVDJob.fromLapack(j.lapack()));
        }
    }

    @Test
    public void testEnumCount() {
        assertEquals(4, SVDJob.values().length);
    }
}

