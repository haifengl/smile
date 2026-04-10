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
package smile.math.distance;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JensenShannonDistance.
 *
 * @author Haifeng Li
 */
public class JensenShannonDistanceTest {

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testIdenticalDistributions() {
        System.out.println("identical distributions");
        double[] p = {0.25, 0.25, 0.25, 0.25};
        JensenShannonDistance jsd = new JensenShannonDistance();
        assertEquals(0.0, jsd.d(p, p), 1E-9);
    }

    @Test
    public void testSymmetry() {
        System.out.println("symmetry");
        double[] p = {0.5, 0.3, 0.2};
        double[] q = {0.2, 0.5, 0.3};
        JensenShannonDistance jsd = new JensenShannonDistance();
        assertEquals(jsd.d(p, q), jsd.d(q, p), 1E-9);
    }

    @Test
    public void testNonNegativity() {
        System.out.println("non-negativity");
        double[] p = {0.4, 0.4, 0.2};
        double[] q = {0.1, 0.6, 0.3};
        JensenShannonDistance jsd = new JensenShannonDistance();
        assertTrue(jsd.d(p, q) >= 0.0);
    }

    @Test
    public void testBoundedByOne() {
        System.out.println("bounded by one (sqrt of log 2)");
        // JS divergence <= log(2), so JS distance = sqrt(JSD) <= 1 when using natural log
        double[] p = {1.0, 0.0};
        double[] q = {0.0, 1.0};
        JensenShannonDistance jsd = new JensenShannonDistance();
        assertTrue(jsd.d(p, q) <= 1.0 + 1E-9);
    }

    @Test
    public void testKnownValue() {
        System.out.println("known value");
        // Two identical distributions: distance = 0
        double[] p = {0.3, 0.4, 0.3};
        double[] q = {0.3, 0.4, 0.3};
        JensenShannonDistance jsd = new JensenShannonDistance();
        assertEquals(0.0, jsd.d(p, q), 1E-9);

        // Nearly identical: distance should be very small but positive
        double[] r = {0.3, 0.4, 0.3};
        double[] s = {0.31, 0.39, 0.30};
        assertTrue(jsd.d(r, s) > 0.0);
        assertTrue(jsd.d(r, s) < 0.1);
    }

    @Test
    public void testLengthMismatch() {
        System.out.println("length mismatch");
        JensenShannonDistance jsd = new JensenShannonDistance();
        assertThrows(IllegalArgumentException.class,
                () -> jsd.d(new double[]{0.5, 0.5}, new double[]{0.3, 0.3, 0.4}));
    }

    @Test
    public void testToString() {
        System.out.println("toString");
        assertTrue(new JensenShannonDistance().toString().contains("Jensen-Shannon"));
    }
}

