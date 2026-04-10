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
package smile.math;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Histogram utility class.
 *
 * @author Haifeng Li
 */
public class HistogramTest {

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    @Test
    public void testBins() {
        System.out.println("bins (square-root rule)");
        assertEquals(10, Histogram.bins(100));
        assertEquals(14, Histogram.bins(200));
        assertEquals(5, Histogram.bins(10));  // minimum of 5
    }

    @Test
    public void testSturges() {
        System.out.println("sturges");
        assertEquals(8, Histogram.sturges(100));
        assertEquals(9, Histogram.sturges(200));
        assertEquals(5, Histogram.sturges(10));  // minimum of 5
    }

    @Test
    public void testIntArray() {
        System.out.println("of(int[], int)");
        int[] data = {1, 2, 2, 3, 3, 3, 4, 4, 5};
        double[][] hist = Histogram.of(data, 5);
        assertEquals(3, hist.length);
        // Check that frequency sums to total count
        double total = 0;
        for (double v : hist[2]) {
            total += v;
        }
        assertEquals(data.length, (int) total);
    }

    @Test
    public void testDoubleArray() {
        System.out.println("of(double[], int)");
        double[] data = {1.0, 2.0, 2.0, 3.0, 3.0, 3.0, 4.0, 4.0, 5.0};
        double[][] hist = Histogram.of(data, 4);
        assertEquals(3, hist.length);
        assertEquals(4, hist[0].length);
        assertEquals(4, hist[1].length);
        assertEquals(4, hist[2].length);
        // bins cover the full range
        assertEquals(data[0], hist[0][0], 1E-10);
        assertEquals(data[data.length - 1], hist[1][hist[1].length - 1], 1E-10);
        // total count
        double total = 0;
        for (double v : hist[2]) {
            total += v;
        }
        assertEquals(data.length, (int) total);
    }

    @Test
    public void testFloatArray() {
        System.out.println("of(float[], int)");
        float[] data = {1.0f, 2.0f, 2.0f, 3.0f, 3.0f, 3.0f, 4.0f, 4.0f, 5.0f};
        double[][] hist = Histogram.of(data, 4);
        assertEquals(3, hist.length);
        assertEquals(4, hist[0].length);
        double total = 0;
        for (double v : hist[2]) {
            total += v;
        }
        assertEquals(data.length, (int) total);
    }

    @Test
    public void testSqrtChoiceDouble() {
        System.out.println("of(double[]) square-root choice");
        MathEx.setSeed(19650218);
        double[] data = MathEx.random(0.0, 10.0, 100);
        double[][] hist = Histogram.of(data);
        assertEquals(3, hist.length);
        double total = 0;
        for (double v : hist[2]) {
            total += v;
        }
        assertEquals(100, (int) total);
    }

    @Test
    public void testBreaksWithWidth() {
        System.out.println("breaks(double[], double)");
        double[] x = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0};
        double[] breaks = Histogram.breaks(x, 1.0);
        // 5 bins for range [0,5], width 1
        assertEquals(5, breaks.length - 1);
        for (int i = 1; i < breaks.length; i++) {
            assertEquals(1.0, breaks[i] - breaks[i-1], 1E-10);
        }
    }

    @Test
    public void testBreaksWithCount() {
        System.out.println("breaks(double, double, int)");
        double[] breaks = Histogram.breaks(0.0, 10.0, 5);
        assertEquals(6, breaks.length);
        assertEquals(0.0, breaks[0], 1E-10);
        assertEquals(10.0, breaks[breaks.length - 1], 1E-10);
    }

    @Test
    public void testScottRule() {
        System.out.println("scott");
        MathEx.setSeed(19650218);
        double[] data = MathEx.randn(1000);
        int k = Histogram.scott(data);
        assertTrue(k >= 5 && k <= 100, "Scott rule bins should be in reasonable range: " + k);
    }

    @Test
    public void testIntArrayWithBreaks() {
        System.out.println("of(int[], double[])");
        int[] data = {1, 2, 3, 4, 5};
        double[] breaks = {0.5, 2.5, 4.5, 5.5};
        double[][] hist = Histogram.of(data, breaks);
        assertEquals(3, hist.length);
        assertEquals(3, hist[0].length);
        assertEquals(2.0, hist[2][0], 1E-10); // 1,2
        assertEquals(2.0, hist[2][1], 1E-10); // 3,4
        assertEquals(1.0, hist[2][2], 1E-10); // 5
    }
}

