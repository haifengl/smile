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
 * Tests for the Scaler class.
 *
 * @author Haifeng Li
 */
public class ScalerTest {

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    @Test
    public void testMinMax() {
        System.out.println("minmax");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0};
        Scaler scaler = Scaler.minmax(data);
        // min=1, max=5 => scale=4, offset=1
        assertEquals(0.0, scaler.f(1.0), 1E-10);
        assertEquals(1.0, scaler.f(5.0), 1E-10);
        assertEquals(0.5, scaler.f(3.0), 1E-10);
        // clipping
        assertEquals(0.0, scaler.f(0.0), 1E-10);  // below min -> clipped to 0
        assertEquals(1.0, scaler.f(6.0), 1E-10);  // above max -> clipped to 1
    }

    @Test
    public void testMinMaxInverse() {
        System.out.println("minmax inverse");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0};
        Scaler scaler = Scaler.minmax(data);
        assertEquals(1.0, scaler.inv(0.0), 1E-10);
        assertEquals(5.0, scaler.inv(1.0), 1E-10);
        assertEquals(3.0, scaler.inv(0.5), 1E-10);
    }

    @Test
    public void testStandardizer() {
        System.out.println("standardizer");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        Scaler scaler = Scaler.standardizer(data);
        double mean = MathEx.mean(data);
        double std = MathEx.stdev(data);
        // f(x) = (x - mean) / std
        assertEquals(0.0, scaler.f(mean), 1E-10);
        assertEquals(1.0, scaler.f(mean + std), 1E-10);
        assertEquals(-1.0, scaler.f(mean - std), 1E-10);
    }

    @Test
    public void testStandardizerInverse() {
        System.out.println("standardizer inverse");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        Scaler scaler = Scaler.standardizer(data);
        double mean = MathEx.mean(data);
        assertEquals(mean, scaler.inv(0.0), 1E-10);
    }

    @Test
    public void testRobustStandardizer() {
        System.out.println("standardizer (robust)");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        Scaler scaler = Scaler.standardizer(data, true);
        // f(median) should be 0
        assertEquals(0.0, scaler.f(5.0), 0.1); // median ~5 for this data
    }

    @Test
    public void testWinsor() {
        System.out.println("winsor");
        double[] data = new double[100];
        for (int i = 0; i < 100; i++) {
            data[i] = i + 1.0; // 1..100
        }
        Scaler scaler = Scaler.winsor(data, 0.05, 0.95);
        // values within [5th,95th] percentile should be in [0,1]
        double v = scaler.f(50.0);
        assertTrue(v >= 0.0 && v <= 1.0);
        // values below 5th percentile clipped to 0
        assertEquals(0.0, scaler.f(1.0), 1E-10);
        // values above 95th percentile clipped to 1
        assertEquals(1.0, scaler.f(100.0), 1E-10);
    }

    @Test
    public void testScalerOf_MinMax() {
        System.out.println("Scaler.of minmax");
        double[] data = {0.0, 10.0};
        Scaler scaler = Scaler.of("minmax", data);
        assertNotNull(scaler);
        assertEquals(0.0, scaler.f(0.0), 1E-10);
        assertEquals(1.0, scaler.f(10.0), 1E-10);
    }

    @Test
    public void testScalerOf_Standardizer() {
        System.out.println("Scaler.of standardizer");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0};
        Scaler scaler = Scaler.of("standardizer", data);
        assertNotNull(scaler);
        double mean = MathEx.mean(data);
        assertEquals(0.0, scaler.f(mean), 1E-10);
    }

    @Test
    public void testScalerOf_Null() {
        System.out.println("Scaler.of null");
        double[] data = {1.0, 2.0, 3.0};
        assertNull(Scaler.of(null, data));
        assertNull(Scaler.of("", data));
    }

    @Test
    public void testZeroScale() {
        System.out.println("zero scale defaults to 1");
        // When scale is zero, should default to 1 (no-op scaling)
        Scaler scaler = new Scaler(0.0, 5.0, false);
        assertEquals(3.0 - 5.0, scaler.f(3.0), 1E-10);
    }

    @Test
    public void testRoundTrip() {
        System.out.println("round-trip f(inv(x)) == x");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0};
        Scaler scaler = Scaler.standardizer(data);
        double[] values = {1.5, 2.5, 3.5};
        for (double v : values) {
            assertEquals(v, scaler.inv(scaler.f(v)), 1E-10);
        }
    }
}

