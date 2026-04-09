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
 * Tests for {@link Order}.
 */
public class OrderTest {

    @Test
    public void testBlasAndLapackValues() {
        assertEquals(101, Order.ROW_MAJOR.blas());
        assertEquals(102, Order.COL_MAJOR.blas());
        // blas() and lapack() must agree
        assertEquals(Order.ROW_MAJOR.blas(), Order.ROW_MAJOR.lapack());
        assertEquals(Order.COL_MAJOR.blas(), Order.COL_MAJOR.lapack());
    }

    @Test
    public void testIsRowMajor() {
        assertTrue(Order.ROW_MAJOR.isRowMajor());
        assertFalse(Order.COL_MAJOR.isRowMajor());
    }

    @Test
    public void testIsColMajor() {
        assertTrue(Order.COL_MAJOR.isColMajor());
        assertFalse(Order.ROW_MAJOR.isColMajor());
    }

    @Test
    public void testIsRowMajorAndIsColMajorAreMutuallyExclusive() {
        for (Order o : Order.values()) {
            assertNotEquals(o.isRowMajor(), o.isColMajor(),
                    "isRowMajor and isColMajor must be mutually exclusive for " + o);
        }
    }

    @Test
    public void testDescription() {
        assertNotNull(Order.ROW_MAJOR.description());
        assertNotNull(Order.COL_MAJOR.description());
        assertNotEquals(Order.ROW_MAJOR.description(), Order.COL_MAJOR.description());
    }

    @Test
    public void testFromValue() {
        assertSame(Order.ROW_MAJOR, Order.fromValue(101));
        assertSame(Order.COL_MAJOR, Order.fromValue(102));
    }

    @Test
    public void testFromValueUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Order.fromValue(999));
    }

    @Test
    public void testRoundTrip() {
        for (Order o : Order.values()) {
            assertSame(o, Order.fromValue(o.blas()));
        }
    }

    @Test
    public void testEnumCount() {
        assertEquals(2, Order.values().length);
    }
}

