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
 * Tests for {@link ShellSort}.
 *
 * @author Haifeng Li
 */
public class ShellSortTest {

    // -----------------------------------------------------------------------
    // int[]
    // -----------------------------------------------------------------------

    @Test
    public void testSortInt() {
        System.out.println("ShellSort int");
        int[] expected = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        int[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ShellSort.sort(data1);
        assertArrayEquals(expected, data1);

        int[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        ShellSort.sort(data2);
        assertArrayEquals(expected, data2);

        int[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        ShellSort.sort(data3);
        assertArrayEquals(expected, data3);

        int[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        ShellSort.sort(data4);
        assertArrayEquals(expected, data4);
    }

    // -----------------------------------------------------------------------
    // float[]
    // -----------------------------------------------------------------------

    @Test
    public void testSortFloat() {
        System.out.println("ShellSort float");
        float[] expected = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        float[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ShellSort.sort(data1);
        assertArrayEquals(expected, data1, 1E-6f);

        float[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        ShellSort.sort(data2);
        assertArrayEquals(expected, data2, 1E-6f);
    }

    // -----------------------------------------------------------------------
    // double[]
    // -----------------------------------------------------------------------

    @Test
    public void testSortDouble() {
        System.out.println("ShellSort double");
        double[] expected = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        double[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ShellSort.sort(data1);
        assertArrayEquals(expected, data1, 1E-10);

        double[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        ShellSort.sort(data2);
        assertArrayEquals(expected, data2, 1E-10);

        double[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        ShellSort.sort(data3);
        assertArrayEquals(expected, data3, 1E-10);

        double[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        ShellSort.sort(data4);
        assertArrayEquals(expected, data4, 1E-10);
    }

    // -----------------------------------------------------------------------
    // Generic T[]
    // -----------------------------------------------------------------------

    @Test
    public void testSortObject() {
        System.out.println("ShellSort object");
        Integer[] expected = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        Integer[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ShellSort.sort(data1);
        assertArrayEquals(expected, data1);

        Integer[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        ShellSort.sort(data2);
        assertArrayEquals(expected, data2);

        Integer[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        ShellSort.sort(data3);
        assertArrayEquals(expected, data3);
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    public void testSingleElement() {
        System.out.println("ShellSort single element");
        int[] a = {42};
        ShellSort.sort(a);
        assertEquals(42, a[0]);
    }

    @Test
    public void testTwoElements() {
        System.out.println("ShellSort two elements");
        int[] a = {2, 1};
        ShellSort.sort(a);
        assertArrayEquals(new int[]{1, 2}, a);

        int[] b = {1, 2};
        ShellSort.sort(b);
        assertArrayEquals(new int[]{1, 2}, b);
    }

    @Test
    public void testAllEqual() {
        System.out.println("ShellSort all equal");
        int[] a = {5, 5, 5, 5, 5};
        ShellSort.sort(a);
        for (int v : a) assertEquals(5, v);

        double[] d = {3.14, 3.14, 3.14};
        ShellSort.sort(d);
        for (double v : d) assertEquals(3.14, v, 1E-10);
    }

    @Test
    public void testNegativeValues() {
        System.out.println("ShellSort negative values");
        int[] a = {3, -1, -5, 0, 2, -3};
        ShellSort.sort(a);
        assertArrayEquals(new int[]{-5, -3, -1, 0, 2, 3}, a);
    }

    @Test
    public void testStrings() {
        System.out.println("ShellSort strings");
        String[] s = {"banana", "apple", "cherry", "date"};
        ShellSort.sort(s);
        assertArrayEquals(new String[]{"apple", "banana", "cherry", "date"}, s);
    }

    // -----------------------------------------------------------------------
    // Large random sort
    // -----------------------------------------------------------------------

    @Test
    public void testSortBig() {
        System.out.println("ShellSort big array");
        double[] big = new double[1000000];
        for (int i = 0; i < big.length; i++) big[i] = MathEx.random();
        ShellSort.sort(big);
        for (int i = 0; i < big.length - 1; i++) {
            assertTrue(big[i] <= big[i + 1], "Not sorted at " + i);
        }
    }
}