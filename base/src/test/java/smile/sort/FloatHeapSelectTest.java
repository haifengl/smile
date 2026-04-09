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
 * Tests for {@link FloatHeapSelect}.
 *
 * @author Haifeng Li
 */
public class FloatHeapSelectTest {

    @Test
    public void testBasic() {
        System.out.println("FloatHeapSelect basic");
        FloatHeapSelect heap = new FloatHeapSelect(9);
        heap.add(63); heap.add(107);
        heap.add(53); heap.add(106);
        heap.add(98); heap.add(105);
        heap.add(100); heap.add(104);
        heap.add(101); heap.add(103);
        heap.add(3);   heap.add(102);
        heap.add(54);  heap.add(99);
        heap.add(89);  heap.add(91);

        assertEquals(3,   heap.get(0), 1E-6f);
        assertEquals(53,  heap.get(1), 1E-6f);
        assertEquals(54,  heap.get(2), 1E-6f);
        assertEquals(63,  heap.get(3), 1E-6f);
        assertEquals(89,  heap.get(4), 1E-6f);
        assertEquals(91,  heap.get(5), 1E-6f);
        assertEquals(98,  heap.get(6), 1E-6f);
        assertEquals(99,  heap.get(7), 1E-6f);
        assertEquals(100, heap.get(8), 1E-6f);
    }

    @Test
    public void testAscendingStream() {
        System.out.println("FloatHeapSelect ascending stream");
        FloatHeapSelect instance = new FloatHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(0.1f * i);
            if (i > 10) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(0.1f * j, instance.get(j), 1E-6f);
                }
            }
        }
    }

    @Test
    public void testDescendingStream() {
        System.out.println("FloatHeapSelect descending stream");
        FloatHeapSelect instance = new FloatHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add((1000 - i) * 0.1f);
            if (i >= 9) {
                for (int j = 0; j < 10; j++) {
                    assertEquals((1000 - i + j) * 0.1f, instance.get(j), 1E-6f);
                }
            }
        }
    }

    @Test
    public void testKEqualsOne() {
        System.out.println("FloatHeapSelect k=1");
        FloatHeapSelect hs = new FloatHeapSelect(1);
        hs.add(5.0f);
        hs.add(3.0f);
        hs.add(7.0f);
        hs.add(1.0f);
        hs.add(9.0f);
        assertEquals(1.0f, hs.get(0), 1E-6f);
        assertEquals(1.0f, hs.peek(), 1E-6f);
    }

    @Test
    public void testPeek() {
        System.out.println("FloatHeapSelect peek");
        FloatHeapSelect hs = new FloatHeapSelect(3);
        hs.add(10.0f);
        hs.add(5.0f);
        hs.add(20.0f);
        hs.add(1.0f);
        // largest among top-3 smallest is peek()
        assertEquals(10.0f, hs.peek(), 1E-6f);
    }

    @Test
    public void testSize() {
        System.out.println("FloatHeapSelect size");
        FloatHeapSelect hs = new FloatHeapSelect(5);
        assertEquals(0, hs.size());
        hs.add(1.0f);
        assertEquals(1, hs.size());
        hs.add(2.0f); hs.add(3.0f); hs.add(4.0f); hs.add(5.0f);
        assertEquals(5, hs.size());
        hs.add(6.0f);
        assertEquals(6, hs.size());
    }

    @Test
    public void testToArray() {
        System.out.println("FloatHeapSelect toArray");
        FloatHeapSelect hs = new FloatHeapSelect(3);
        hs.add(4.0f);
        hs.add(2.0f);
        hs.add(6.0f);
        hs.add(1.0f);
        float[] arr = hs.toArray();
        assertEquals(3, arr.length);
        hs.sort();
        assertEquals(1.0f, hs.get(0), 1E-6f);
        assertEquals(2.0f, hs.get(1), 1E-6f);
        assertEquals(4.0f, hs.get(2), 1E-6f);
    }

    @Test
    public void testPartialFill() {
        System.out.println("FloatHeapSelect partial fill (n < k)");
        FloatHeapSelect hs = new FloatHeapSelect(10);
        hs.add(7.0f);
        hs.add(3.0f);
        hs.add(5.0f);
        assertEquals(3, hs.size());
        assertEquals(3.0f, hs.get(0), 1E-6f);
        assertEquals(5.0f, hs.get(1), 1E-6f);
        assertEquals(7.0f, hs.get(2), 1E-6f);
    }

    @Test
    public void testAllEqual() {
        System.out.println("FloatHeapSelect all equal values");
        FloatHeapSelect hs = new FloatHeapSelect(5);
        for (int i = 0; i < 20; i++) hs.add(3.14f);
        for (int i = 0; i < 5; i++) {
            assertEquals(3.14f, hs.get(i), 1E-6f);
        }
    }

    @Test
    public void testNegativeValues() {
        System.out.println("FloatHeapSelect negative values");
        FloatHeapSelect hs = new FloatHeapSelect(3);
        hs.add(-1.0f);
        hs.add(-5.0f);
        hs.add(-3.0f);
        hs.add(-10.0f);
        hs.add(0.0f);
        assertEquals(-10.0f, hs.get(0), 1E-6f);
        assertEquals(-5.0f,  hs.get(1), 1E-6f);
        assertEquals(-3.0f,  hs.get(2), 1E-6f);
    }

    @Test
    public void testOutOfBoundsThrows() {
        System.out.println("FloatHeapSelect out-of-bounds get throws");
        FloatHeapSelect hs = new FloatHeapSelect(5);
        hs.add(1.0f);
        hs.add(2.0f);
        assertThrows(IllegalArgumentException.class, () -> hs.get(2));
    }

    @Test
    public void testBig() {
        System.out.println("FloatHeapSelect big stream");
        FloatHeapSelect instance = new FloatHeapSelect(10);
        for (int i = 0; i < 100000000; i++) {
            instance.add((float) MathEx.random());
        }
        for (int j = 0; j < 9; j++) {
            assertTrue(instance.get(j) <= instance.get(j + 1),
                    "Expected sorted order at index " + j);
        }
    }
}
