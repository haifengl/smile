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

import java.util.Arrays;
import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link QuickSort}.
 *
 * @author Haifeng Li
 */
public class QuickSortTest {

    // -----------------------------------------------------------------------
    // sort(int[]) — returns index permutation
    // -----------------------------------------------------------------------

    @Test
    public void testSortInt() {
        System.out.println("sort int");
        int[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] order1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertArrayEquals(order1, QuickSort.sort(data1));
        int[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        int[] order2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        assertArrayEquals(order2, QuickSort.sort(data2));
        int[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        int[] order3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        assertArrayEquals(order3, QuickSort.sort(data3));
        int[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        int[] order4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        assertArrayEquals(order4, QuickSort.sort(data4));
    }

    @Test
    public void testSortFloat() {
        System.out.println("sort float");
        float[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] order1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertArrayEquals(order1, QuickSort.sort(data1));
        float[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        int[] order2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        assertArrayEquals(order2, QuickSort.sort(data2));
    }

    @Test
    public void testSortDouble() {
        System.out.println("sort double");
        double[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] order1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertArrayEquals(order1, QuickSort.sort(data1));
        double[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        int[] order2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        assertArrayEquals(order2, QuickSort.sort(data2));
    }

    @Test
    public void testSortObject() {
        System.out.println("sort object");
        Integer[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] order1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertArrayEquals(order1, QuickSort.sort(data1));
        Integer[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        int[] order2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        assertArrayEquals(order2, QuickSort.sort(data2));
    }

    // -----------------------------------------------------------------------
    // Co-sorting correctness
    // -----------------------------------------------------------------------

    @Test
    public void testCoSortIntInt() {
        System.out.println("co-sort int[] + int[]");
        int[] x = {3, 1, 4, 1, 5, 9, 2, 6};
        int[] y = {30, 10, 40, 10, 50, 90, 20, 60};
        QuickSort.sort(x, y);
        // After sort x is non-decreasing; y must follow x
        for (int i = 0; i < x.length - 1; i++) {
            assertTrue(x[i] <= x[i + 1], "x not sorted at " + i);
        }
        // Spot-check: x[0]=1 (was index 1 or 3), y should be 10
        assertEquals(1, x[0]);
        assertEquals(10, y[0]);
        assertEquals(1, x[1]);
        assertEquals(10, y[1]);
    }

    @Test
    public void testCoSortIntDouble() {
        System.out.println("co-sort int[] + double[]");
        int[] x = {5, 2, 8, 1, 9};
        double[] y = {50.0, 20.0, 80.0, 10.0, 90.0};
        QuickSort.sort(x, y);
        assertArrayEquals(new int[]{1, 2, 5, 8, 9}, x);
        assertEquals(10.0, y[0], 1E-10);
        assertEquals(20.0, y[1], 1E-10);
        assertEquals(50.0, y[2], 1E-10);
        assertEquals(80.0, y[3], 1E-10);
        assertEquals(90.0, y[4], 1E-10);
    }

    @Test
    public void testCoSortDoubleInt() {
        System.out.println("co-sort double[] + int[]");
        double[] x = {3.0, 1.0, 4.0, 1.0, 5.0};
        int[] y    = {300, 100, 400, 100, 500};
        QuickSort.sort(x, y);
        assertEquals(1.0, x[0], 1E-10);
        assertEquals(100, y[0]);
        assertEquals(1.0, x[1], 1E-10);
        assertEquals(100, y[1]);
        assertEquals(5.0, x[4], 1E-10);
        assertEquals(500, y[4]);
    }

    @Test
    public void testCoSortDoubleDouble() {
        System.out.println("co-sort double[] + double[]");
        double[] x = {9.0, 3.0, 7.0, 1.0};
        double[] y = {90.0, 30.0, 70.0, 10.0};
        QuickSort.sort(x, y);
        assertEquals(1.0, x[0], 1E-10);
        assertEquals(10.0, y[0], 1E-10);
        assertEquals(9.0, x[3], 1E-10);
        assertEquals(90.0, y[3], 1E-10);
    }

    @Test
    public void testCoSortIntObject() {
        System.out.println("co-sort int[] + Object[]");
        int[] x      = {3, 1, 2};
        String[] y   = {"three", "one", "two"};
        QuickSort.sort(x, y);
        assertArrayEquals(new int[]{1, 2, 3}, x);
        assertEquals("one",   y[0]);
        assertEquals("two",   y[1]);
        assertEquals("three", y[2]);
    }

    @Test
    public void testCoSortFloatInt() {
        System.out.println("co-sort float[] + int[]");
        float[] x = {5.0f, 2.0f, 8.0f, 1.0f};
        int[]   y = {50,   20,   80,   10};
        QuickSort.sort(x, y);
        assertEquals(1.0f, x[0], 1E-6f);
        assertEquals(10, y[0]);
        assertEquals(8.0f, x[3], 1E-6f);
        assertEquals(80, y[3]);
    }

    // -----------------------------------------------------------------------
    // Partial sort (first n elements)
    // -----------------------------------------------------------------------

    @Test
    public void testPartialSort() {
        System.out.println("partial sort (n elements)");
        int[] x = {5, 3, 1, 4, 2, 9, 8};
        int[] y = {50, 30, 10, 40, 20, 90, 80};
        QuickSort.sort(x, y, 5); // sort only first 5
        // First 5 should be sorted: 1,2,3,4,5
        int[] first5x = Arrays.copyOf(x, 5);
        Arrays.sort(first5x);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, first5x);
        // Last 2 unchanged
        assertEquals(9, x[5]);
        assertEquals(8, x[6]);
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    public void testSingleElement() {
        System.out.println("single element");
        int[] x = {42};
        int[] y = {420};
        QuickSort.sort(x, y);
        assertEquals(42, x[0]);
        assertEquals(420, y[0]);
    }

    @Test
    public void testTwoElements() {
        System.out.println("two elements");
        int[] x = {2, 1};
        int[] y = {20, 10};
        QuickSort.sort(x, y);
        assertArrayEquals(new int[]{1, 2}, x);
        assertArrayEquals(new int[]{10, 20}, y);
    }

    @Test
    public void testAllEqual() {
        System.out.println("all equal");
        int[] x = {3, 3, 3, 3, 3};
        int[] y = {1, 2, 3, 4, 5};
        QuickSort.sort(x, y);
        for (int v : x) assertEquals(3, v);
    }

    // -----------------------------------------------------------------------
    // Large random sort
    // -----------------------------------------------------------------------

    @Test
    public void testSortBig() {
        System.out.println("sort big array");
        double[] big = new double[1000000];
        for (int i = 0; i < big.length; i++) big[i] = MathEx.random();
        int[] order = QuickSort.sort(big);
        // big is now sorted; verify
        for (int i = 0; i < big.length - 1; i++) {
            assertTrue(big[i] <= big[i + 1], "Not sorted at " + i);
        }
        // order must be a permutation of [0, n)
        int[] sorted = order.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < sorted.length; i++) assertEquals(i, sorted[i]);
    }
}