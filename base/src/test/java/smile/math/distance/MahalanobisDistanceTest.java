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
 *
 * @author Haifeng Li
 */
public class MahalanobisDistanceTest {
    double[][] sigma =
    {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.7000, 0.3000, 0.8000}
    };

    public MahalanobisDistanceTest() {
    }

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

    /**
     * Test of distance method, of class MahalanobisDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        double[] x = {1.2793, -0.1029, -1.5852};
        double[] y = {-0.2676, -0.1717, -1.8695};

        MahalanobisDistance instance = new MahalanobisDistance(sigma);
        assertEquals(2.703861, instance.d(x, y), 1E-6);
    }

    @Test
    public void testSymmetry() {
        System.out.println("symmetry");
        double[] x = {1.2793, -0.1029, -1.5852};
        double[] y = {-0.2676, -0.1717, -1.8695};

        MahalanobisDistance instance = new MahalanobisDistance(sigma);
        assertEquals(instance.d(x, y), instance.d(y, x), 1E-9);
    }

    @Test
    public void testSamePoint() {
        System.out.println("same point");
        double[] x = {1.2793, -0.1029, -1.5852};
        MahalanobisDistance instance = new MahalanobisDistance(sigma);
        assertEquals(0.0, instance.d(x, x), 1E-9);
    }

    @Test
    public void testNonNegativity() {
        System.out.println("non-negativity");
        double[] x = {0.5, -0.3, 1.2};
        double[] y = {-0.1, 0.8, -0.5};
        MahalanobisDistance instance = new MahalanobisDistance(sigma);
        assertTrue(instance.d(x, y) >= 0.0);
    }

    @Test
    public void testDimensionMismatch() {
        System.out.println("dimension mismatch");
        MahalanobisDistance instance = new MahalanobisDistance(sigma);
        assertThrows(IllegalArgumentException.class,
                () -> instance.d(new double[]{1.0, 2.0}, new double[]{1.0, 2.0, 3.0}));
    }

    @Test
    public void testIdentityCovariance() {
        System.out.println("identity covariance");
        // When covariance is identity, Mahalanobis == Euclidean
        double[][] identity = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        MahalanobisDistance mah = new MahalanobisDistance(identity);
        double[] x = {3.0, 0.0, 0.0};
        double[] y = {0.0, 0.0, 0.0};
        assertEquals(3.0, mah.d(x, y), 1E-9);
    }
}