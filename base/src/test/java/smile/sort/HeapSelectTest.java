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
 * Tests for {@link HeapSelect}.
 *
 * @author Haifeng Li
 */
public class HeapSelectTest {

    @Test
    public void testBasic() {
        System.out.println("HeapSelect basic");
        HeapSelect<Integer> heap = new HeapSelect<>(Integer.class, 9);
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
        System.out.println("HeapSelect ascending stream");
        HeapSelect<Integer> instance = new HeapSelect<>(Integer.class, 10);
        for (int i = 0; i < 1000; i++) {
            instance.add(i);
            if (i > 10) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(Integer.valueOf(j), instance.get(j));
                }
            }
        }
    }

    @Test
    public void testDescendingStream() {
        System.out.println("HeapSelect descending stream");
        HeapSelect<Integer> instance = new HeapSelect<>(Integer.class, 10);
        for (int i = 0; i < 1000; i++) {
            instance.add(1000 - i);
            if (i >= 9) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(Integer.valueOf(1000 - i + j), instance.get(j));
                }
            }
        }
    }

    @Test
    public void testKEqualsOne() {
        System.out.println("HeapSelect k=1");
        HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 1);
        hs.add(5); hs.add(3); hs.add(7); hs.add(1); hs.add(9);
        assertEquals(Integer.valueOf(1), hs.get(0));
        assertEquals(Integer.valueOf(1), hs.peek());
    }

    @Test
    public void testPeek() {
        System.out.println("HeapSelect peek");
        HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 3);
        hs.add(10); hs.add(5); hs.add(20); hs.add(1);
        // largest among top-3 smallest is peek()
        assertEquals(Integer.valueOf(10), hs.peek());
    }

    @Test
    public void testSize() {
        System.out.println("HeapSelect size");
        HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 5);
        assertEquals(0, hs.size());
        hs.add(1);
        assertEquals(1, hs.size());
        hs.add(2); hs.add(3); hs.add(4); hs.add(5);
        assertEquals(5, hs.size());
        hs.add(6);
        assertEquals(6, hs.size());
    }

    @Test
    public void testToArray() {
        System.out.println("HeapSelect toArray");
        HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 3);
        hs.add(4); hs.add(2); hs.add(6); hs.add(1);
        Integer[] arr = hs.toArray();
        assertEquals(3, arr.length);
        hs.sort();
        assertEquals(Integer.valueOf(1), hs.get(0));
        assertEquals(Integer.valueOf(2), hs.get(1));
        assertEquals(Integer.valueOf(4), hs.get(2));
    }

    @Test
    public void testToArrayWithBuffer() {
        System.out.println("HeapSelect toArray(T[])");
        HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 3);
        hs.add(10); hs.add(5); hs.add(3); hs.add(7);
        Integer[] buf = new Integer[3];
        hs.toArray(buf);
        assertEquals(3, buf.length);
    }

    @Test
    public void testPartialFill() {
        System.out.println("HeapSelect partial fill (n < k)");
        HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 10);
        hs.add(7); hs.add(3); hs.add(5);
        assertEquals(3, hs.size());
        assertEquals(Integer.valueOf(3), hs.get(0));
        assertEquals(Integer.valueOf(5), hs.get(1));
        assertEquals(Integer.valueOf(7), hs.get(2));
    }

    @Test
    public void testAllEqual() {
        System.out.println("HeapSelect all equal values");
        HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 5);
        for (int i = 0; i < 20; i++) hs.add(42);
        for (int i = 0; i < 5; i++) {
            assertEquals(Integer.valueOf(42), hs.get(i));
        }
    }

    @Test
    public void testNegativeValues() {
        System.out.println("HeapSelect negative values");
        HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 3);
        hs.add(-1); hs.add(-5); hs.add(-3); hs.add(-10); hs.add(0);
        assertEquals(Integer.valueOf(-10), hs.get(0));
        assertEquals(Integer.valueOf(-5),  hs.get(1));
        assertEquals(Integer.valueOf(-3),  hs.get(2));
    }

    @Test
    public void testOutOfBoundsThrows() {
        System.out.println("HeapSelect out-of-bounds get throws");
        HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 5);
        hs.add(1); hs.add(2);
        assertThrows(IllegalArgumentException.class, () -> hs.get(2));
    }

    @Test
    public void testWithStrings() {
        System.out.println("HeapSelect with String");
        HeapSelect<String> hs = new HeapSelect<>(String.class, 3);
        hs.add("banana"); hs.add("apple"); hs.add("cherry");
        hs.add("date"); hs.add("avocado");
        assertEquals("apple",   hs.get(0));
        assertEquals("avocado", hs.get(1));
        assertEquals("banana",  hs.get(2));
    }

    @Test
    public void testBig() {
        System.out.println("HeapSelect big stream");
        HeapSelect<Double> instance = new HeapSelect<>(Double.class, 10);
        for (int i = 0; i < 100000000; i++) {
            instance.add(MathEx.random());
        }
        for (int j = 0; j < 9; j++) {
            assertTrue(instance.get(j) <= instance.get(j + 1),
                    "Expected sorted order at index " + j);
        }
    }
}
