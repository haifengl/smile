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
package smile.sort;

import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DoubleHeapSelect}.
 *
 * @author Haifeng Li
 */
public class DoubleHeapSelectTest {

    @Test
    public void testBasic() {
        System.out.println("DoubleHeapSelect basic");
        DoubleHeapSelect heap = new DoubleHeapSelect(9);
        heap.add(63); heap.add(107);
        heap.add(53); heap.add(106);
        heap.add(98); heap.add(105);
        heap.add(100); heap.add(104);
        heap.add(101); heap.add(103);
        heap.add(3);   heap.add(102);
        heap.add(54);  heap.add(99);
        heap.add(89);  heap.add(91);

        assertEquals(3,   heap.get(0), 1E-10);
        assertEquals(53,  heap.get(1), 1E-10);
        assertEquals(54,  heap.get(2), 1E-10);
        assertEquals(63,  heap.get(3), 1E-10);
        assertEquals(89,  heap.get(4), 1E-10);
        assertEquals(91,  heap.get(5), 1E-10);
        assertEquals(98,  heap.get(6), 1E-10);
        assertEquals(99,  heap.get(7), 1E-10);
        assertEquals(100, heap.get(8), 1E-10);
    }

    @Test
    public void testAscendingStream() {
        System.out.println("DoubleHeapSelect ascending stream");
        DoubleHeapSelect instance = new DoubleHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(0.1 * i);
            if (i > 10) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(0.1 * j, instance.get(j), 1E-10);
                }
            }
        }
    }

    @Test
    public void testDescendingStream() {
        System.out.println("DoubleHeapSelect descending stream");
        DoubleHeapSelect instance = new DoubleHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(0.1 * (1000 - i));
            if (i >= 9) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(0.1 * (1000 - i + j), instance.get(j), 1E-10);
                }
            }
        }
    }

    @Test
    public void testKEqualsOne() {
        System.out.println("DoubleHeapSelect k=1");
        DoubleHeapSelect hs = new DoubleHeapSelect(1);
        hs.add(5.0);
        hs.add(3.0);
        hs.add(7.0);
        hs.add(1.0);
        hs.add(9.0);
        assertEquals(1.0, hs.get(0), 1E-10);
        assertEquals(1.0, hs.peek(), 1E-10);
    }

    @Test
    public void testPeek() {
        System.out.println("DoubleHeapSelect peek");
        DoubleHeapSelect hs = new DoubleHeapSelect(3);
        hs.add(10.0);
        hs.add(5.0);
        hs.add(20.0);
        hs.add(1.0);
        // largest among top-3 smallest is peek()
        assertEquals(10.0, hs.peek(), 1E-10);
    }

    @Test
    public void testSize() {
        System.out.println("DoubleHeapSelect size");
        DoubleHeapSelect hs = new DoubleHeapSelect(5);
        assertEquals(0, hs.size());
        hs.add(1.0);
        assertEquals(1, hs.size());
        hs.add(2.0); hs.add(3.0); hs.add(4.0); hs.add(5.0);
        assertEquals(5, hs.size());
        hs.add(6.0);
        assertEquals(6, hs.size());
    }

    @Test
    public void testToArray() {
        System.out.println("DoubleHeapSelect toArray");
        DoubleHeapSelect hs = new DoubleHeapSelect(3);
        hs.add(4.0);
        hs.add(2.0);
        hs.add(6.0);
        hs.add(1.0);
        double[] arr = hs.toArray();
        assertEquals(3, arr.length);
        hs.sort();
        assertEquals(1.0, hs.get(0), 1E-10);
        assertEquals(2.0, hs.get(1), 1E-10);
        assertEquals(4.0, hs.get(2), 1E-10);
    }

    @Test
    public void testPartialFill() {
        System.out.println("DoubleHeapSelect partial fill (n < k)");
        DoubleHeapSelect hs = new DoubleHeapSelect(10);
        hs.add(7.0);
        hs.add(3.0);
        hs.add(5.0);
        assertEquals(3, hs.size());
        assertEquals(3.0, hs.get(0), 1E-10);
        assertEquals(5.0, hs.get(1), 1E-10);
        assertEquals(7.0, hs.get(2), 1E-10);
    }

    @Test
    public void testAllEqual() {
        System.out.println("DoubleHeapSelect all equal values");
        DoubleHeapSelect hs = new DoubleHeapSelect(5);
        for (int i = 0; i < 20; i++) hs.add(3.14);
        for (int i = 0; i < 5; i++) {
            assertEquals(3.14, hs.get(i), 1E-10);
        }
    }

    @Test
    public void testNegativeValues() {
        System.out.println("DoubleHeapSelect negative values");
        DoubleHeapSelect hs = new DoubleHeapSelect(3);
        hs.add(-1.0);
        hs.add(-5.0);
        hs.add(-3.0);
        hs.add(-10.0);
        hs.add(0.0);
        assertEquals(-10.0, hs.get(0), 1E-10);
        assertEquals(-5.0,  hs.get(1), 1E-10);
        assertEquals(-3.0,  hs.get(2), 1E-10);
    }

    @Test
    public void testOutOfBoundsThrows() {
        System.out.println("DoubleHeapSelect out-of-bounds get throws");
        DoubleHeapSelect hs = new DoubleHeapSelect(5);
        hs.add(1.0);
        hs.add(2.0);
        assertThrows(IllegalArgumentException.class, () -> hs.get(2));
    }

    @Test
    public void testBig() {
        System.out.println("DoubleHeapSelect big stream");
        DoubleHeapSelect instance = new DoubleHeapSelect(10);
        for (int i = 0; i < 100000000; i++) {
            instance.add(MathEx.random());
        }
        for (int j = 0; j < 9; j++) {
            assertTrue(instance.get(j) <= instance.get(j + 1),
                    "Expected sorted order at index " + j);
        }
    }
}
