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

import org.junit.jupiter.api.*;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IntHeapSelect}.
 *
 * @author Haifeng Li
 */
public class IntHeapSelectTest {

    @Test
    public void testBasic() {
        System.out.println("IntHeapSelect basic");
        IntHeapSelect heap = new IntHeapSelect(9);
        heap.add(63); heap.add(107);
        heap.add(53); heap.add(106);
        heap.add(98); heap.add(105);
        heap.add(100); heap.add(104);
        heap.add(101); heap.add(103);
        heap.add(3);   heap.add(102);
        heap.add(54);  heap.add(99);
        heap.add(89);  heap.add(91);

        assertEquals(3,   heap.get(0));
        assertEquals(53,  heap.get(1));
        assertEquals(54,  heap.get(2));
        assertEquals(63,  heap.get(3));
        assertEquals(89,  heap.get(4));
        assertEquals(91,  heap.get(5));
        assertEquals(98,  heap.get(6));
        assertEquals(99,  heap.get(7));
        assertEquals(100, heap.get(8));
    }

    @Test
    public void testAscendingStream() {
        System.out.println("IntHeapSelect ascending stream");
        IntHeapSelect instance = new IntHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(i);
            if (i > 10) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(j, instance.get(j));
                }
            }
        }
    }

    @Test
    public void testDescendingStream() {
        System.out.println("IntHeapSelect descending stream");
        IntHeapSelect instance = new IntHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(1000 - i);
            if (i >= 9) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(1000 - i + j, instance.get(j));
                }
            }
        }
    }

    @Test
    public void testKEqualsOne() {
        System.out.println("IntHeapSelect k=1");
        IntHeapSelect hs = new IntHeapSelect(1);
        hs.add(5);
        hs.add(3);
        hs.add(7);
        hs.add(1);
        hs.add(9);
        assertEquals(1, hs.get(0));
        assertEquals(1, hs.peek());
    }

    @Test
    public void testPeek() {
        System.out.println("IntHeapSelect peek");
        IntHeapSelect hs = new IntHeapSelect(3);
        hs.add(10);
        hs.add(5);
        hs.add(20);
        hs.add(1);
        // largest among top-3 smallest is peek()
        assertEquals(10, hs.peek());
    }

    @Test
    public void testSize() {
        System.out.println("IntHeapSelect size");
        IntHeapSelect hs = new IntHeapSelect(5);
        assertEquals(0, hs.size());
        hs.add(1);
        assertEquals(1, hs.size());
        hs.add(2);
        hs.add(3);
        hs.add(4);
        hs.add(5);
        assertEquals(5, hs.size());
        // size keeps growing past k
        hs.add(6);
        assertEquals(6, hs.size());
    }

    @Test
    public void testToArray() {
        System.out.println("IntHeapSelect toArray");
        IntHeapSelect hs = new IntHeapSelect(3);
        hs.add(4);
        hs.add(2);
        hs.add(6);
        hs.add(1);
        int[] arr = hs.toArray();
        assertEquals(3, arr.length);
        // The array holds the 3 smallest: {1,2,4} in heap order
        // After sort(), descending: [4, 2, 1] at indices 1..3
        hs.sort();
        assertEquals(1, hs.get(0));
        assertEquals(2, hs.get(1));
        assertEquals(4, hs.get(2));
    }

    @Test
    public void testPartialFill() {
        System.out.println("IntHeapSelect partial fill (n < k)");
        IntHeapSelect hs = new IntHeapSelect(10);
        hs.add(7);
        hs.add(3);
        hs.add(5);
        assertEquals(3, hs.size());
        assertEquals(3, hs.get(0));
        assertEquals(5, hs.get(1));
        assertEquals(7, hs.get(2));
    }

    @Test
    public void testAllEqual() {
        System.out.println("IntHeapSelect all equal values");
        IntHeapSelect hs = new IntHeapSelect(5);
        for (int i = 0; i < 20; i++) hs.add(42);
        for (int i = 0; i < 5; i++) {
            assertEquals(42, hs.get(i));
        }
    }

    @Test
    public void testNegativeValues() {
        System.out.println("IntHeapSelect negative values");
        IntHeapSelect hs = new IntHeapSelect(3);
        hs.add(-1);
        hs.add(-5);
        hs.add(-3);
        hs.add(-10);
        hs.add(0);
        assertEquals(-10, hs.get(0));
        assertEquals(-5,  hs.get(1));
        assertEquals(-3,  hs.get(2));
    }

    @Test
    public void testOutOfBoundsThrows() {
        System.out.println("IntHeapSelect out-of-bounds get throws");
        IntHeapSelect hs = new IntHeapSelect(5);
        hs.add(1);
        hs.add(2);
        assertThrows(IllegalArgumentException.class, () -> hs.get(2));
    }

    @Test
    public void testBig() {
        System.out.println("IntHeapSelect big stream");
        IntHeapSelect instance = new IntHeapSelect(10);
        for (int i = 0; i < 100000000; i++) {
            instance.add(MathEx.randomInt(1000000));
        }
        for (int j = 0; j < 9; j++) {
            assertTrue(instance.get(j) <= instance.get(j + 1),
                    "Expected sorted order at index " + j);
        }
    }
}