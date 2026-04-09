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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link QuickSelect}.
 *
 * @author Haifeng Li
 */
public class QuickSelectTest {

    // -----------------------------------------------------------------------
    // median — int
    // -----------------------------------------------------------------------

    @Test
    public void testMedianInt() {
        System.out.println("median int");
        int[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1));
        int[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2));
        int[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3));
        int[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4));
        int[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5));
        int[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6));
    }

    // -----------------------------------------------------------------------
    // median — float
    // -----------------------------------------------------------------------

    @Test
    public void testMedianFloat() {
        System.out.println("median float");
        float[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1), 1E-10);
        float[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2), 1E-10);
        float[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3), 1E-10);
        float[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4), 1E-10);
        float[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5), 1E-10);
        float[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6), 1E-10);
    }

    // -----------------------------------------------------------------------
    // median — double
    // -----------------------------------------------------------------------

    @Test
    public void testMedianDouble() {
        System.out.println("median double");
        double[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1), 1E-10);
        double[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2), 1E-10);
        double[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3), 1E-10);
        double[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4), 1E-10);
        double[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5), 1E-10);
        double[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6), 1E-10);
    }

    // -----------------------------------------------------------------------
    // median — generic
    // -----------------------------------------------------------------------

    @Test
    public void testMedianObject() {
        System.out.println("median object");
        Integer[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1));
        Integer[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2));
        Integer[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4));
    }

    // -----------------------------------------------------------------------
    // select — direct k value
    // -----------------------------------------------------------------------

    @Test
    public void testSelectIntDirect() {
        System.out.println("select int k=0 (min)");
        int[] data = {7, 3, 5, 1, 9, 2};
        assertEquals(1, QuickSelect.select(data, 0));

        System.out.println("select int k=last (max)");
        int[] data2 = {7, 3, 5, 1, 9, 2};
        assertEquals(9, QuickSelect.select(data2, 5));
    }

    @Test
    public void testSelectDoubleDirect() {
        System.out.println("select double k=0 (min)");
        double[] data = {7.0, 3.0, 5.0, 1.0, 9.0, 2.0};
        assertEquals(1.0, QuickSelect.select(data, 0), 1E-10);

        System.out.println("select double k=last (max)");
        double[] data2 = {7.0, 3.0, 5.0, 1.0, 9.0, 2.0};
        assertEquals(9.0, QuickSelect.select(data2, 5), 1E-10);
    }

    @Test
    public void testSelectSingleElement() {
        System.out.println("select single element");
        int[] data = {42};
        assertEquals(42, QuickSelect.select(data, 0));
        assertEquals(42, QuickSelect.median(data));

        double[] dd = {3.14};
        assertEquals(3.14, QuickSelect.select(dd, 0), 1E-10);
    }

    @Test
    public void testSelectAllEqual() {
        System.out.println("select all equal");
        int[] data = {5, 5, 5, 5, 5};
        assertEquals(5, QuickSelect.select(data, 0));
        assertEquals(5, QuickSelect.select(data, 2));
        assertEquals(5, QuickSelect.select(data, 4));
        assertEquals(5, QuickSelect.median(data));
    }

    @Test
    public void testSelectTwoElements() {
        System.out.println("select two elements");
        int[] data1 = {2, 1};
        assertEquals(1, QuickSelect.select(data1, 0));
        assertEquals(2, QuickSelect.select(data1, 1));

        int[] data2 = {1, 2};
        assertEquals(1, QuickSelect.select(data2, 0));
        assertEquals(2, QuickSelect.select(data2, 1));
    }

    // -----------------------------------------------------------------------
    // q1 and q3
    // -----------------------------------------------------------------------

    @Test
    public void testQ1Int() {
        System.out.println("q1 int");
        int[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        // k = 8/4 = 2  →  3rd smallest = 3
        assertEquals(3, QuickSelect.q1(data));
    }

    @Test
    public void testQ3Int() {
        System.out.println("q3 int");
        int[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        // k = 3*8/4 = 6  →  7th smallest = 7
        assertEquals(7, QuickSelect.q3(data));
    }

    @Test
    public void testQ1Double() {
        System.out.println("q1 double");
        double[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        assertEquals(3.0, QuickSelect.q1(data), 1E-10);
    }

    @Test
    public void testQ3Double() {
        System.out.println("q3 double");
        double[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        assertEquals(7.0, QuickSelect.q3(data), 1E-10);
    }

    @Test
    public void testQ1Float() {
        System.out.println("q1 float");
        float[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        assertEquals(3.0f, QuickSelect.q1(data), 1E-6f);
    }

    @Test
    public void testQ3Float() {
        System.out.println("q3 float");
        float[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        assertEquals(7.0f, QuickSelect.q3(data), 1E-6f);
    }

    @Test
    public void testQ1Q3Object() {
        System.out.println("q1/q3 object");
        Integer[] data = {8, 2, 5, 1, 4, 7, 3, 6};
        assertEquals(Integer.valueOf(3), QuickSelect.q1(data));
        assertEquals(Integer.valueOf(7), QuickSelect.q3(data));
    }

    @Test
    public void testQ1LtMedianLtQ3() {
        System.out.println("q1 < median < q3");
        double[] data = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5};
        double[] copy1 = data.clone();
        double[] copy2 = data.clone();
        double[] copy3 = data.clone();
        double q1 = QuickSelect.q1(copy1);
        double med = QuickSelect.median(copy2);
        double q3 = QuickSelect.q3(copy3);
        assertTrue(q1 <= med, "q1 should be <= median");
        assertTrue(med <= q3, "median should be <= q3");
    }
}
